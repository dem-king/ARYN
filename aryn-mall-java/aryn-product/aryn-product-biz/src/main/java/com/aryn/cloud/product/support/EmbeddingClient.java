package com.aryn.cloud.product.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

	private final boolean enabled;

	private final String apiKey;

	private final String model;

	private final String baseUrl;

	private final Integer dimensions;

	private final Duration timeout;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	public EmbeddingClient(
			@Value("${aryn.embedding.enabled:true}") boolean enabled,
			@Value("${aryn.embedding.api-key:}") String apiKey,
			@Value("${aryn.embedding.model:embedding-3}") String model,
			@Value("${aryn.embedding.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
			@Value("${aryn.embedding.dimensions:1024}") Integer dimensions,
			@Value("${aryn.embedding.timeout-ms:20000}") long timeoutMs) {
		this.enabled = enabled;
		this.apiKey = apiKey;
		this.model = model;
		this.baseUrl = baseUrl;
		this.dimensions = dimensions;
		this.timeout = Duration.ofMillis(timeoutMs);
	}

	/** 开关打开且配了 key 才可用；未配置时调用方跳过向量召回，走纯字面匹配 */
	public boolean isAvailable() {
		return enabled && StringUtils.hasText(apiKey);
	}

	/**
	 * 批量向量化，返回顺序与输入一一对应。
	 *
	 * <p>空串/空白输入原样返回零向量占位是不安全的（会与任何向量算出 0 分）
	 * ——调用方必须保证传入非空白文本；这里对空白串直接抛错暴露问题。
	 */
	public List<float[]> embed(List<String> texts) {
		if (texts == null || texts.isEmpty()) {
			return List.of();
		}
		List<float[]> result = new ArrayList<>(texts.size());
		for (int from = 0; from < texts.size(); from += MAX_BATCH) {
			List<String> chunk = texts.subList(from, Math.min(from + MAX_BATCH, texts.size()));
			for (String text : chunk) {
				if (!StringUtils.hasText(text)) {
					throw new IllegalStateException("embedding 输入不能包含空白文本");
				}
			}
			result.addAll(embedChunk(chunk));
		}
		return result;
	}

	private List<float[]> embedChunk(List<String> chunk) {
		JSONObject body = new JSONObject();
		body.put("model", model);
		List<String> input = chunk.stream()
			.map(text -> text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text)
			.toList();
		body.put("input", input);
		if (dimensions != null && dimensions > 0) {
			body.put("dimensions", dimensions);
		}
		String content = request(body.toJSONString());
		return parseEmbeddings(content, chunk.size());
	}

	private String request(String requestBody) {
		// 与 ChainAiAssistant 同口径：5 秒内的瞬时拒绝（限流/过载）立即重试一次，
		// 超时类失败不重试——调用方有字面匹配兜底，不能让用户干等两倍时长
		for (int attempt = 1; attempt <= 2; attempt++) {
			long startedAt = System.nanoTime();
			try {
				HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/embeddings"))
					.timeout(timeout)
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() != 200) {
					throw new IllegalStateException("embedding 接口 HTTP " + response.statusCode() + "：" + truncate(response.body()));
				}
				JSONObject root = JSON.parseObject(response.body());
				JSONArray data = root.getJSONArray("data");
				if (data == null || data.isEmpty()) {
					throw new IllegalStateException("embedding 返回缺少 data");
				}
				return response.body();
			}
			catch (java.io.IOException | InterruptedException exception) {
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				throw new IllegalStateException("embedding 接口调用失败：" + exception.getMessage(), exception);
			}
			catch (IllegalStateException exception) {
				boolean fastFailure = System.nanoTime() - startedAt < FAST_FAIL_RETRY_WINDOW_NANOS;
				if (attempt == 2 || !fastFailure) {
					throw exception;
				}
				log.warn("embedding 接口快速失败（{}），重试一次", exception.getMessage());
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/** 5 秒内返回的错误视为「瞬时拒绝」（过载/限流），值得立即重试一次 */
	private static final long FAST_FAIL_RETRY_WINDOW_NANOS = 5_000_000_000L;

	/**
	 * 解析 /embeddings 响应：按 index 对齐并校验条数（静态包可见，便于脱离 HTTP 单测）。
	 */
	static List<float[]> parseEmbeddings(String content, int expectedSize) {
		JSONObject root;
		try {
			root = JSON.parseObject(content);
		}
		catch (Exception exception) {
			throw new IllegalStateException("embedding 返回不是合法 JSON：" + truncate(content));
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
