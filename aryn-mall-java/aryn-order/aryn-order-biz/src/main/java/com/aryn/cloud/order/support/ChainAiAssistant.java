package com.aryn.cloud.order.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接龙报货的 AI 助手（OpenAI 兼容协议，HTTP 直连不引 SDK；默认 DeepSeek，
 * GLM 等同协议供应商换 base-url/model 即可）。
 *
 * <p>分工边界（工程上决定这个功能是「好用」还是「翻车」的四条线）：
 * <ol>
 *   <li><b>一次粘贴 = 至多三次批量调用</b>：解析、缺品别名、选品各一次，不做逐行请求；
 *       别名一次只在缺品行出现且 {@code alias-recall-enabled} 打开时触发（默认关，
 *       缺品主路径是商品域向量召回）；</li>
 *   <li><b>AI 管理解和选品，不管钱</b>：价格、库存永远来自数据库，模型只输出
 *       结构化行、检索别名与候选 SKU 的选择——别名也只是检索线索，
 *       命中哪个商品永远由商品库决定；</li>
 *   <li><b>规则是兜底</b>：{@code isAvailable()=false}（未配 key/开关关闭）或本次
 *       调用失败/输出不合法时，调用方整体回退 {@link ChainOrderTextParser} 与
 *       规则候选排序——AI 永远不是链路上的单点；</li>
 *   <li><b>确认台是闸门</b>：AI 的选品只是把候选排到首位并打上
 *       {@code aiPickedSkuId}，采用与否仍由人在报告页决定。</li>
 * </ol>
 *
 * <p>反幻觉：选品结果里的 skuId 必须**真的在该行候选集里**，行号必须真的存在，
 * 否则直接丢弃——模型可以选错，但不允许凭空造商品。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Slf4j
@Component
public class ChainAiAssistant {

	/** 单次解析允许的最大行数（与补给单导入上限对齐） */
	private static final int MAX_ITEMS = 2000;

	/** 选品 prompt 里每行候选上限：候选是模糊匹配按分排好的，头部最可信 */
	private static final int MAX_CANDIDATES_PER_ROW = 10;

	/** 别名建议每行上限：召回按词逐条 LIKE，3 个词足够覆盖常见同物异名 */
	private static final int MAX_TERMS_PER_ROW = 3;

	/** 单个别名的长度上限：超长的「别名」多半是把数量规格带了进来 */
	private static final int MAX_TERM_LENGTH = 30;

	private static final String PARSE_SYSTEM_PROMPT = """
			你是船员报货接龙的解析助手。把用户粘贴的微信群接龙报货文本解析成 JSON 数组输出，不要输出解释、不要 markdown 代码块。
			每个元素的字段：
			- person：报货人姓名或职务原文（如「任化东」「水手长」）；该条没有报人就填 null
			- name：商品名。去掉数量和包装规格串（如 600ml*24瓶、500ML12罐、24罐装），但保留区分商品的口味/品种/做法词（如「青香蕉」「熟点」「蒜香」「卤牛腱子」）；「红和黄 各一斤」这类要拆成多条（name 写「小番茄 红」「小番茄 黄」）
			- spec：包装规格原文，没有则 null
			- quantity：正整数。中文数字（四、两、二十）转阿拉伯数字；「各一斤」按每样 1；数量不明填 null，不要替用户猜；规格里的数字不是数量
			- unit：数量单位（斤/箱/包/瓶/罐/串/个等），没有则 null
			- note：该条的备注原文（如「红和黄 各一斤」），没有则 null
			只输出 JSON 数组。""";

	private static final String PICK_SYSTEM_PROMPT = """
			你是商品匹配助手。输入若干报货品名，每行带商城候选商品列表。为每行从候选中选出最可能是同一种商品的一个 skuId，都不像就填 null。
			判断口径：说的是同一种东西就算（橙子=脐橙=鲜橙；青香蕉=香蕉的青皮品种；卷饼皮=手抓卷饼皮）；品牌/口味/加工方式不同不算（芬达橙味≠橙子，番茄酱≠小番茄，黄冰糖≠老冰糖）。
			只输出 JSON：{"picks":[{"rowNo":行号,"skuId":"候选里的skuId或null"}]}，不要解释。""";

	private static final String SUGGEST_SYSTEM_PROMPT = """
			你是商品名联想助手。输入的报货品名在商城商品库里没有直接匹配，多半是叫法不同。为每个品名想 2~3 个商城里更常见的叫法或别名，用于到商品库搜索同一种东西。
			要求：只给同一种东西的通行叫法（西红柿→番茄、卷心菜→包菜、土豆→马铃薯、地瓜→红薯）；品牌名与加工口味变体不算（芬达≠橙子、番茄酱≠番茄）；不要带数量、包装、规格词；想不出就给空数组。
			只输出 JSON：{"suggests":[{"rowNo":行号,"terms":["别名"]}]}，不要解释。""";

	private final boolean enabled;

	private final boolean aliasRecallEnabled;

	private final String apiKey;

	private final String model;

	private final String baseUrl;

	private final Duration timeout;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	public ChainAiAssistant(
			@Value("${aryn.chain-ai.enabled:true}") boolean enabled,
			@Value("${aryn.chain-ai.alias-recall-enabled:false}") boolean aliasRecallEnabled,
			@Value("${aryn.chain-ai.api-key:}") String apiKey,
			@Value("${aryn.chain-ai.model:deepseek-chat}") String model,
			@Value("${aryn.chain-ai.base-url:https://api.deepseek.com}") String baseUrl,
			@Value("${aryn.chain-ai.timeout-ms:20000}") long timeoutMs) {
		this.enabled = enabled;
		this.aliasRecallEnabled = aliasRecallEnabled;
		this.apiKey = apiKey;
		this.model = model;
		this.baseUrl = baseUrl;
		this.timeout = Duration.ofMillis(timeoutMs);
	}

	/** 开关打开且配了 key 才可用；未配置时调用方走纯规则路径 */
	public boolean isAvailable() {
		return enabled && StringUtils.hasText(apiKey);
	}

	/**
	 * AI 别名召回开关（默认关）：缺品候选的主路径已由商品域的向量召回接管
	 * （确定性、无采样），本类的 suggestSearchTerms 保留为备胎——向量供应商
	 * 不可用时可临时打开顶上，用 LLM 采样别名再查一轮字面。
	 */
	public boolean isAliasRecallEnabled() {
		return aliasRecallEnabled;
	}

	/** AI 解析出的单行（复用规则解析器的行结构，管道其余部分零改动） */
	public List<ChainOrderTextParser.ParsedItem> parseChain(String text) {
		String content = chat(buildChatBody(PARSE_SYSTEM_PROMPT, "接龙原文：\n" + text));
		return parseChainContent(content);
	}

	/**
	 * AI 从各行的模糊匹配候选里挑最像的一个。
	 *
	 * @return rowNo → skuId；只保留「行号真实存在且 skuId 真在候选集里」的选择
	 */
	public Map<Integer, String> pickSkus(Map<Integer, List<ReplenishImportMatchVO.Candidate>> candidatesByRowNo) {
		JSONArray items = new JSONArray();
		candidatesByRowNo.forEach((rowNo, candidates) -> {
			JSONObject item = new JSONObject();
			item.put("rowNo", rowNo);
			JSONArray list = new JSONArray();
			candidates.stream().limit(MAX_CANDIDATES_PER_ROW).forEach(candidate -> {
				JSONObject entry = new JSONObject();
				entry.put("skuId", candidate.getSkuId());
				entry.put("name", candidate.getName());
				if (StringUtils.hasText(candidate.getSpec())) {
					entry.put("spec", candidate.getSpec());
				}
				list.add(entry);
			});
			item.put("candidates", list);
			items.add(item);
		});
		String content = chat(buildChatBody(PICK_SYSTEM_PROMPT, items.toJSONString()));
		return parsePickContent(content, candidatesByRowNo);
	}

	/**
	 * 为「零候选」的缺品行产出别名搜索词。
	 *
	 * <p>二字组/单字召回都是字面重叠，叫法与商城名零重叠时（西红柿/番茄）
	 * 商品域的候选池是空的，选品再聪明也没得挑。别名只是**检索线索**——
	 * 调用方拿它再查一轮商品库，命中什么仍由数据库决定；模型在这里
	 * 连商品都接触不到，幻觉无从谈起，只需防「换汤不换药」的无效词。
	 *
	 * @return rowNo → 清洗后的别名（≤{@link #MAX_TERMS_PER_ROW} 条）；只保留行号真实存在的行
	 */
	public Map<Integer, List<String>> suggestSearchTerms(Map<Integer, String> namesByRowNo) {
		JSONArray items = new JSONArray();
		namesByRowNo.forEach((rowNo, name) -> {
			JSONObject item = new JSONObject();
			item.put("rowNo", rowNo);
			item.put("name", name);
			items.add(item);
		});
		String content = chat(buildChatBody(SUGGEST_SYSTEM_PROMPT, items.toJSONString()));
		return parseSuggestContent(content, namesByRowNo);
	}

	/** 以下解析逻辑抽成静态包可见，便于脱离 HTTP 单测 */
	static List<ChainOrderTextParser.ParsedItem> parseChainContent(String content) {
		JSONArray array = extractArray(content);
		List<ChainOrderTextParser.ParsedItem> items = new ArrayList<>();
		int lineNo = 0;
		for (int index = 0; index < array.size() && items.size() < MAX_ITEMS; index++) {
			JSONObject row = array.getJSONObject(index);
			if (row == null) {
				continue;
			}
			String name = trimToNull(row.getString("name"));
			if (!StringUtils.hasText(name)) {
				continue;
			}
			Integer quantity = parseIntSafe(row.get("quantity"));
			String spec = trimToNull(row.getString("spec"));
			String note = trimToNull(row.getString("note"));
			String segment = note != null ? note : name;
			if (spec != null) {
				segment = segment + "（规格：" + spec + "）";
			}
			// 疑点按行产生：只有数量没识别出来（模型按 prompt 约定填 null）兜底成 1 时才提示。
			// 不给每行挂「AI 解析行，请核对」的大伞——报告顶部的「AI 解析」徽标已告知
			// 整体来源，满屏同一句警告只会把真疑点（默认数量）淹没。
			boolean quantityMissing = quantity == null || quantity < 1;
			items.add(new ChainOrderTextParser.ParsedItem(++lineNo,
					trimToNull(row.getString("person")),
					name,
					quantityMissing ? 1 : Math.min(quantity, 999999),
					trimToNull(row.getString("unit")),
					segment,
					quantityMissing ? "未识别到数量，默认 1，请核对" : null));
		}
		if (items.isEmpty()) {
			throw new IllegalStateException("AI 解析结果为空");
		}
		return items;
	}

	static Map<Integer, String> parsePickContent(String content,
			Map<Integer, List<ReplenishImportMatchVO.Candidate>> candidatesByRowNo) {
		JSONObject root = extractObject(content);
		JSONArray picks = root.getJSONArray("picks");
		Map<Integer, String> result = new LinkedHashMap<>();
		if (picks == null) {
			return result;
		}
		for (int index = 0; index < picks.size(); index++) {
			JSONObject pick = picks.getJSONObject(index);
			if (pick == null) {
				continue;
			}
			Integer rowNo = pick.getInteger("rowNo");
			String skuId = trimToNull(pick.getString("skuId"));
			if (rowNo == null || !StringUtils.hasText(skuId)) {
				continue;
			}
			List<ReplenishImportMatchVO.Candidate> candidates = candidatesByRowNo.get(rowNo);
			if (candidates == null) {
				continue;
			}
			boolean known = candidates.stream().anyMatch(candidate -> skuId.equals(candidate.getSkuId()));
			if (known) {
				result.put(rowNo, skuId);
			}
		}
		return result;
	}

	/**
	 * 别名输出的防御式解析：行号必须真实存在；别名去空白、去重、
	 * 限长（过短 LIKE 噪声大、过长多半带了规格）、与原名同义的丢弃。
	 */
	static Map<Integer, List<String>> parseSuggestContent(String content, Map<Integer, String> namesByRowNo) {
		JSONObject root = extractObject(content);
		JSONArray suggests = root.getJSONArray("suggests");
		Map<Integer, List<String>> result = new LinkedHashMap<>();
		if (suggests == null) {
			return result;
		}
		for (int index = 0; index < suggests.size(); index++) {
			JSONObject suggest = suggests.getJSONObject(index);
			if (suggest == null) {
				continue;
			}
			Integer rowNo = suggest.getInteger("rowNo");
			String original = rowNo == null ? null : namesByRowNo.get(rowNo);
			if (original == null) {
				continue;
			}
			JSONArray terms = suggest.getJSONArray("terms");
			if (terms == null) {
				continue;
			}
			List<String> cleaned = new ArrayList<>();
			for (int termIndex = 0; termIndex < terms.size() && cleaned.size() < MAX_TERMS_PER_ROW; termIndex++) {
				String term = trimToNull(terms.getString(termIndex));
				if (term == null) {
					continue;
				}
				String normalized = ReplenishMatchRules.normalizeName(term);
				// 过短（单字 LIKE 噪声大）、过长（多半带了数量规格）的都不要
				if (normalized.length() > MAX_TERM_LENGTH || normalized.length() < 2) {
					continue;
				}
				if (normalized.equals(ReplenishMatchRules.normalizeName(original))) {
					continue;
				}
				boolean duplicated = cleaned.stream()
					.anyMatch(existing -> ReplenishMatchRules.normalizeName(existing).equals(normalized));
				if (!duplicated) {
					cleaned.add(term);
				}
			}
			if (!cleaned.isEmpty()) {
				result.put(rowNo, cleaned);
			}
		}
		return result;
	}

	private String buildChatBody(String systemPrompt, String userContent) {
		JSONObject body = new JSONObject();
		body.put("model", model);
		JSONArray messages = new JSONArray();
		JSONObject system = new JSONObject();
		system.put("role", "system");
		system.put("content", systemPrompt);
		JSONObject user = new JSONObject();
		user.put("role", "user");
		user.put("content", userContent);
		messages.add(system);
		messages.add(user);
		body.put("messages", messages);
		body.put("temperature", 0.1);
		// 结构化提取不需要思考链：GLM-4.5/4.7 关掉后更快，也不会被推理吃掉输出预算
		// （glm-4 系实测容忍该参数）；max_tokens 留足 25 行接龙的 JSON 输出
		JSONObject thinking = new JSONObject();
		thinking.put("type", "disabled");
		body.put("thinking", thinking);
		body.put("max_tokens", 8192);
		return body.toJSONString();
	}

	private String chat(String requestBody) {
		// 过载（1305/429/5xx）这类秒回错误重试一次性价比高；超时类失败（已耗满
		// timeout）不重试——解析有规则兜底，不能让用户干等两倍时长
		for (int attempt = 1; attempt <= 2; attempt++) {
			long startedAt = System.nanoTime();
			try {
				HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/chat/completions"))
					.timeout(timeout)
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() != 200) {
					throw new IllegalStateException("AI 接口 HTTP " + response.statusCode() + "：" + truncate(response.body()));
				}
				JSONObject root = JSON.parseObject(response.body());
				JSONArray choices = root.getJSONArray("choices");
				if (choices == null || choices.isEmpty()) {
					throw new IllegalStateException("AI 返回缺少 choices");
				}
				String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
				if (!StringUtils.hasText(content)) {
					throw new IllegalStateException("AI 返回内容为空");
				}
				return content;
			}
			catch (java.io.IOException | InterruptedException exception) {
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				throw new IllegalStateException("AI 接口调用失败：" + exception.getMessage(), exception);
			}
			catch (IllegalStateException exception) {
				// 5 秒内返回的错误视为「瞬时拒绝」（过载/限流），值得立即重试一次
				boolean fastFailure = System.nanoTime() - startedAt < FAST_FAIL_RETRY_WINDOW_NANOS;
				if (attempt == 2 || !fastFailure) {
					throw exception;
				}
				log.warn("AI 接口快速失败（{}），重试一次", exception.getMessage());
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/** 5 秒内返回的错误视为「瞬时拒绝」（过载/限流），值得立即重试一次 */
	private static final long FAST_FAIL_RETRY_WINDOW_NANOS = 5_000_000_000L;

	private static String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() <= 200 ? text : text.substring(0, 200) + "…";
	}

	private static String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static Integer parseIntSafe(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.valueOf(value.toString().strip());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	/** 模型输出常带 ```json 围栏或前后废话：截出首个 JSON 块再解析 */
	static String extractJsonBlock(String content) {
		String trimmed = content == null ? "" : content.strip();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\s*", "");
			int fence = trimmed.lastIndexOf("```");
			if (fence >= 0) {
				trimmed = trimmed.substring(0, fence);
			}
			trimmed = trimmed.strip();
		}
		int arrayStart = trimmed.indexOf('[');
		int objectStart = trimmed.indexOf('{');
		if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
			int end = trimmed.lastIndexOf(']');
			if (end > arrayStart) {
				return trimmed.substring(arrayStart, end + 1);
			}
		}
		if (objectStart >= 0) {
			int end = trimmed.lastIndexOf('}');
			if (end > objectStart) {
				return trimmed.substring(objectStart, end + 1);
			}
		}
		return trimmed;
	}

	static JSONArray extractArray(String content) {
		String block = extractJsonBlock(content);
		try {
			return JSON.parseArray(block);
		}
		catch (Exception exception) {
			throw new IllegalStateException("AI 输出不是合法的 JSON 数组：" + truncate(block));
		}
	}

	static JSONObject extractObject(String content) {
		String block = extractJsonBlock(content);
		try {
			return JSON.parseObject(block);
		}
		catch (Exception exception) {
			throw new IllegalStateException("AI 输出不是合法的 JSON 对象：" + truncate(block));
		}
	}

}
