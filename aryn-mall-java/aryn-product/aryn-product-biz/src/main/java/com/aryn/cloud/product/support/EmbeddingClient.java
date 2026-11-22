package com.aryn.cloud.product.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化客户端（OpenAI 兼容 /embeddings 协议，HTTP 直连不引 SDK；默认智谱
 * embedding-3，阿里 text-embedding-v3 等同协议供应商换 base-url/model 即可）。
 *
 * <p>与 order 域的 {@code ChainAiAssistant} 同一套工程纪律，但职责不同：
 * 这里是**编码器不是生成器**——同样的输入永远得到同样的向量，没有采样、
 * 没有幻觉，召回层因此是确定性的；价格库存与商品选择仍由数据库和人负责。
 *
 * <p>批量：目录向量化按 {@link #MAX_BATCH} 条分块顺序请求（一次粘贴的品名
 * 一个块就够）；超时/非 200/输出不合法都抛 {@link IllegalStateException}，
 * 由调用方整体降级回字面匹配，embedding 永远不是链路单点。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Slf4j
@Component
public class EmbeddingClient {

	/** 单次请求的文本条数上限：主流兼容实现（智谱/OpenAI）都在 64~2048 之间，取保守值 */
	private static final int MAX_BATCH = 32;

	/** 单条文本长度上限：商品名远用不到，防御性截断避免异常输入拖垮请求 */
	private static final int MAX_TEXT_LENGTH = 200;

	private final ProductAiRuntime runtime;
    public EmbeddingClient(ProductAiRuntime runtime){this.runtime=runtime;}
    public boolean isAvailable(){return runtime.available(com.aryn.cloud.common.ai.contract.AiUsage.CATALOG_EMBEDDING);}
    public com.aryn.cloud.upms.api.client.AiRuntimeClient.Session open(String task){return runtime.open(com.aryn.cloud.common.ai.contract.AiUsage.CATALOG_EMBEDDING,task);}
    public com.aryn.cloud.upms.api.client.AiRuntimeClient.Session openVersion(String task,String version){return runtime.openVersion(com.aryn.cloud.common.ai.contract.AiUsage.CATALOG_EMBEDDING,task,version);}
    public List<float[]> embed(List<String> texts) {
        if(texts==null || texts.isEmpty()) return List.of();
        return embed(open(java.util.UUID.randomUUID().toString()),texts);
    }
    public List<float[]> embed(com.aryn.cloud.upms.api.client.AiRuntimeClient.Session session,List<String> texts) {
        List<float[]> result=new ArrayList<>();
        for(int start=0;start<texts.size();start+=MAX_BATCH) {
            List<String> chunk=texts.subList(start,Math.min(start+MAX_BATCH,texts.size()));
            for(String text:chunk) if(!StringUtils.hasText(text) || text.length()>MAX_TEXT_LENGTH) throw new IllegalStateException("向量输入为空或超长");
            for(var vector:session.embed(chunk).vectors()) { float[] values=new float[vector.size()];for(int i=0;i<values.length;i++) values[i]=vector.get(i).floatValue();result.add(values); }
        }
        return result;
    }

	/**
	 * 解析 /embeddings 响应：按 index 对齐并校验条数（静态包可见，便于脱离 HTTP 单测）。
	 */
	static List<float[]> parseEmbeddings(String content, int expectedSize) {
		JSONObject root;
		try {
			root = JSON.parseObject(content);
		}
		catch (Exception exception) {
			throw new IllegalStateException("embedding 返回不是合法 JSON");
		}
		JSONArray data = root.getJSONArray("data");
		if (data == null || data.size() != expectedSize) {
			throw new IllegalStateException("embedding 返回条数不符：期望 " + expectedSize + "，实际 "
					+ (data == null ? 0 : data.size()));
		}
		float[][] ordered = new float[expectedSize][];
		for (int index = 0; index < data.size(); index++) {
			JSONObject entry = data.getJSONObject(index);
			if (entry == null) {
				continue;
			}
			Integer position = entry.getInteger("index");
			JSONArray vector = entry.getJSONArray("embedding");
			if (position == null || position < 0 || position >= expectedSize || vector == null || vector.isEmpty()) {
				continue;
			}
			float[] values = new float[vector.size()];
			for (int i = 0; i < vector.size(); i++) {
				values[i] = vector.getFloatValue(i);
			}
			ordered[position] = values;
		}
		List<float[]> result = new ArrayList<>(expectedSize);
		for (float[] vector : ordered) {
			if (vector == null) {
				throw new IllegalStateException("embedding 返回缺少向量条目");
			}
			result.add(vector);
		}
		return result;
	}

	private static String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() <= 200 ? text : text.substring(0, 200) + "…";
	}

}
