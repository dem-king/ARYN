package com.aryn.cloud.product.api.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补给单导入匹配的共享口径（纯函数，放在 api 层供商品域与订单域共用）。
 *
 * <p>为什么必须共享而不是各写一份：商品域负责「命中哪个 SKU」，
 * 订单域负责「这行算不算可导入」，两边都要判断规格等价；
 * 各写一份的结果是报告说匹配成功、确认时又被服务端拦下。
 *
 * <p>本类不依赖数据库、不抛业务异常，可脱离 Spring 单测。
 *
 * @author aryn
 * @since 2026/9/22
 */
public final class ReplenishMatchRules {

	/** 规格文本拼接分隔符（与 C 端展示口径一致） */
	public static final String SEPARATOR = "；";

	/**
	 * 模糊匹配自动命中阈值：二字组覆盖率（双向取大）达到该值、且是唯一最高分时
	 * 才自动定品。0.75 能放进「红富士冰糖心 → 红富士苹果冰糖心」（0.8），
	 * 挡住「小番茄 → 番茄酱」（0.5）这类核心词被挪用的误命中——中等重叠
	 * 一律降级为候选让人一键确认。
	 */
	public static final double FUZZY_AUTO_THRESHOLD = 0.75;

	/** 模糊匹配候选建议阈值：低于该值的重叠视为噪声，不进候选 */
	public static final double FUZZY_SUGGEST_THRESHOLD = 0.5;

	private ReplenishMatchRules() {
	}

	/**
	 * 规格描述：SKU 规格值按 {@link #SEPARATOR} 拼接（与 C 端展示口径一致）。
	 *
	 * <p>模板导出与行匹配必须共用这一份拼接：导出写进「规格」列的文本，
	 * 回传时会拿来做「规格变更」比对，两边各拼一份就会出现
	 * 「客户没改规格，却被报规格不一致」。
	 *
	 * @param specsValueNames SKU 规格值名称，可为空
	 * @return 拼接结果；无有效规格值时返回 null（空规格与「无规格」同义）
	 */
	public static String specText(List<String> specsValueNames) {
		if (specsValueNames == null || specsValueNames.isEmpty()) {
			return null;
		}
		String joined = specsValueNames.stream()
			.filter(name -> name != null && !name.isBlank())
			.collect(java.util.stream.Collectors.joining(SEPARATOR));
		return joined.isBlank() ? null : joined;
	}

	/**
	 * 规格规范化：去空白、全角字符/乘号转半角、统一小写。只用于**比较**，不用于展示。
	 */
	public static String normalizeSpec(String spec) {
		if (spec == null) {
			return "";
		}
		String text = java.text.Normalizer.normalize(spec, java.text.Normalizer.Form.NFKC)
			.trim()
			.toLowerCase();
		text = text.replace('（', '(').replace('）', ')')
			.replace('＊', '*').replace('×', '*')
			// 「500mlx24」与「500ml*24」同一包装写法；只替换数字之间的 x，避免误伤 XL 等文本
			.replaceAll("(?<=\\d)[xX](?=\\d)", "*");
		return text.replaceAll("[\\s\\u3000]+", "");
	}

	/** 商品名规范化：去空白，便于「品名 + 规格」等价比较 */
	public static String normalizeName(String name) {
		if (name == null) {
			return "";
		}
		return name.trim().toLowerCase().replaceAll("[\\s\\u3000]+", "");
	}

	/** 规格里的「数字 + 可选单位」片段：500ml、24瓶、2.5kg、10 包 */
	private static final Pattern SPEC_QUANTITY = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*(ml|毫升|kg|千克|公斤|mg|毫克|g|克|l|升|cm|厘米|mm|毫米|m|米|斤|两|瓶|罐|盒|包|袋|支|听|条|枚|粒|片|张|卷|套|组|份)?",
			Pattern.CASE_INSENSITIVE);

	/** 包装描述词：只补充「瓶装/箱装/家庭装」等包装说明，不构成规格差异 */
	private static final Pattern SPEC_PACKAGING = Pattern.compile("(瓶|罐|盒|包|袋|支|听|条|枚|粒|片|张|卷|套|组|份|箱|桶|装)+");

	/** 包装描述短语：整体剥离后才允许「一侧无额外文本」等价 */
	private static final Pattern SPEC_PACKAGING_PHRASE = Pattern.compile(
			"(家庭装|整箱装?|礼盒装?|组合装|独立包装|大包装|小包装|简装|精装|散装)");

	/** 规格分隔符：比较时忽略，只保留语义文本 */
	private static final Pattern SPEC_SEPARATOR = Pattern.compile("[/\\\\*,，、·\\-—_;；\\s]+");

	/**
	 * 规格文本是否等价。
	 *
	 * <p>任一侧为空视为**无法比较**（返回 true，不报规格变更），因为线下清单常常
	 * 不填规格。两边都有值时不再用「互相包含」放行——子串关系会把 500ml 与
	 * 1500ml、1kg 与 11kg 误判为同一规格，用户可能在无人工确认的情况下买错规格。
	 *
	 * <p>比较口径：
	 * <ol>
	 *   <li>先做格式归一（全角/大小写/空白/乘号）；</li>
	 *   <li>抽出所有「数字 + 单位」片段（含包装数量，如 24瓶），逐项比较。
	 *       数字或单位有任一真实差异即不等价；</li>
	 *   <li>剩下的语义文本只允许包装描述补充（瓶/罐/盒/装等）与包含关系，
	 *       且仅当数字片段一致时才成立。</li>
	 * </ol>
	 */
	public static boolean specEquivalent(String excelSpec, String skuSpec) {
		String left = normalizeSpec(excelSpec);
		String right = normalizeSpec(skuSpec);
		if (left.isEmpty() || right.isEmpty()) {
			return true;
		}
		if (left.equals(right)) {
			return true;
		}
		List<String> leftQuantities = specQuantities(left);
		List<String> rightQuantities = specQuantities(right);
		if (!leftQuantities.equals(rightQuantities)) {
			return false;
		}
		// 数字片段一致（或两边都没有数字）时才比较语义文本；
		// 一侧有数字、另一侧没有，说明规格表达不同，交给人工确认。
		if (leftQuantities.isEmpty() != rightQuantities.isEmpty()) {
			return false;
		}
		String leftLiteral = specLiteral(left);
		String rightLiteral = specLiteral(right);
		if (leftLiteral.equals(rightLiteral)) {
			return true;
		}
		// 一侧剥完只剩空串、另一侧还有语义文本（如「无糖」），说明规格表达有额外
		// 差异，不能因为数字相同就放行；只有纯包装描述会在 specLiteral 中被剥空。
		if (leftLiteral.isEmpty() || rightLiteral.isEmpty()) {
			return false;
		}
		return leftLiteral.contains(rightLiteral) || rightLiteral.contains(leftLiteral);
	}

	/** 提取并排序「数字+单位」片段：单位统一别名、数字去掉无意义小数位 */
	private static List<String> specQuantities(String normalizedSpec) {
		List<String> quantities = new ArrayList<>();
		Matcher matcher = SPEC_QUANTITY.matcher(normalizedSpec);
		while (matcher.find()) {
			quantities.add(normalizeNumber(matcher.group(1)) + normalizeUnit(matcher.group(2)));
		}
		quantities.sort(String::compareTo);
		return quantities;
	}

	/** 去掉数字片段与包装描述后剩余的语义文本（分隔符不计） */
	private static String specLiteral(String normalizedSpec) {
		String literal = SPEC_QUANTITY.matcher(normalizedSpec).replaceAll("");
		literal = SPEC_SEPARATOR.matcher(literal).replaceAll("");
		literal = SPEC_PACKAGING_PHRASE.matcher(literal).replaceAll("");
		return SPEC_PACKAGING.matcher(literal).replaceAll("");
	}

	private static String normalizeNumber(String value) {
		try {
			return new BigDecimal(value).stripTrailingZeros().toPlainString();
		}
		catch (NumberFormatException exception) {
			return value;
		}
	}

	private static String normalizeUnit(String unit) {
		if (unit == null || unit.isBlank()) {
			return "";
		}
		return switch (unit.toLowerCase()) {
			case "毫升" -> "ml";
			case "升" -> "l";
			case "千克", "公斤" -> "kg";
			case "毫克" -> "mg";
			case "克" -> "g";
			case "厘米" -> "cm";
			case "毫米" -> "mm";
			case "米" -> "m";
			default -> unit.toLowerCase();
		};
	}

	/**
	 * 数量是否满足 MOQ 与步长（正整数前提下）。
	 */
	public static boolean quantityAcceptable(Integer quantity, Integer moq, Integer stepQty) {
		if (quantity == null || quantity <= 0) {
			return false;
		}
		int min = moq == null || moq < 1 ? 1 : moq;
		int step = stepQty == null || stepQty < 1 ? 1 : stepQty;
		return quantity >= min && quantity % step == 0;
	}

	/**
	 * 超库存时的建议调减量：库存向下取步长、不低于 MOQ；不可行时返回 null。
	 */
	public static Integer suggestedQuantity(Integer stock, Integer moq, Integer stepQty) {
		if (stock == null || stock <= 0) {
			return null;
		}
		int min = moq == null || moq < 1 ? 1 : moq;
		int step = stepQty == null || stepQty < 1 ? 1 : stepQty;
		int candidate = stock - (stock % step);
		if (candidate < min) {
			return null;
		}
		return candidate;
	}

	/**
	 * 把数量原文解析为整数：去掉 Excel 数值单元格残留的 .0，非法返回 null。
	 */
	public static Integer parseQuantity(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.matches("\\d+\\.0+")) {
			text = text.substring(0, text.indexOf('.'));
		}
		try {
			return new BigDecimal(text).intValueExact();
		}
		catch (ArithmeticException | NumberFormatException exception) {
			return null;
		}
	}

	/**
	 * 品名相邻二字组（normalize 后）：「青香蕉」→ [青香, 香蕉]。
	 *
	 * <p>接龙报货的修饰词（青/小/无籽/熟点）常夹在核心词中间，「青香蕉」对
	 * 「国产香蕉」双向包含必失败；二字组把重叠的**核心词**提取出来做召回
	 * 与打分，覆盖这类夹心品名。
	 */
	public static List<String> characterBigrams(String name) {
		String normalized = normalizeName(name);
		List<String> grams = new ArrayList<>();
		for (int index = 0; index + 2 <= normalized.length(); index++) {
			grams.add(normalized.substring(index, index + 2));
		}
		return grams;
	}

	/**
	 * 二字组重叠覆盖率（双向取大，0~1）。
	 *
	 * <p>取「目标覆盖率」与「候选覆盖率」的较大值：只取目标侧会漏掉
	 * 「麒麟无籽西瓜 → 麒麟西瓜」（目标 5 组只重叠 2 组 = 0.4，候选侧 0.67）；
	 * 只取候选侧会把「西瓜 → 西瓜子」类前缀误放进来——那类词由
	 * 包含匹配（LIKE）天然覆盖，二字组只服务包含失效的夹心品名。
	 *
	 * <p>自动命中与候选建议的阈值见 {@link #FUZZY_AUTO_THRESHOLD} /
	 * {@link #FUZZY_SUGGEST_THRESHOLD}。
	 */
	public static double bigramScore(String target, String candidate) {
		List<String> targetGrams = characterBigrams(target);
		Set<String> candidateGrams = new HashSet<>(characterBigrams(candidate));
		if (targetGrams.isEmpty() || candidateGrams.isEmpty()) {
			return 0.0;
		}
		Set<String> targetSet = new HashSet<>(targetGrams);
		long intersection = targetSet.stream().filter(candidateGrams::contains).count();
		if (intersection == 0) {
			return 0.0;
		}
		double targetCoverage = intersection / (double) targetSet.size();
		double candidateCoverage = intersection / (double) candidateGrams.size();
		return Math.max(targetCoverage, candidateCoverage);
	}

	/** 尾部/头部质量形容词：纯修饰词，与商品名无关，匹配前剥离（展示原文不动） */
	private static final String QUALITY_ADJECTIVE = "(优质|新鲜|精品|特级|上等|精选|优选|正宗|超大)+";

	/**
	 * 尾部规格片段：数字 + 可选计量单位 + 可选「*N」+ 可选包装单位 + 可选「装」。
	 * 循环剥离可吃掉连写（「600ml*24瓶」「500ML12罐」「24罐装」「4斤装」）。
	 */
	private static final Pattern TAIL_SPEC = Pattern.compile(
			"[0-9]+(?:\\.[0-9]+)?\\s*(?:ml|毫升|l|升|g|克|kg|千克|斤|两)?(?:\\s*[*×xX]\\s*[0-9]+)?(?:瓶|罐|盒|包|袋|支|听|条|枚|粒)?装?$",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern TAIL_ADJECTIVE = Pattern.compile(QUALITY_ADJECTIVE + "$");

	private static final Pattern HEAD_ADJECTIVE = Pattern.compile("^" + QUALITY_ADJECTIVE);

	/** 剥完规格后可能留下的孤立连接符（「600ml*」的尾星号） */
	private static final Pattern TAIL_DEBRIS = Pattern.compile("[\\s*×xX、,，.。]+$");

	/**
	 * 品名匹配变体（按置信度从高到低）：原文 → 剥修饰词 → 剥规格 → 都剥。
	 *
	 * <p>接龙原文常把修饰词（「卤牛腱子优质」）和规格（「上海盐汽水600ml*24瓶」）
	 * 揉进品名，匹配前各剥一版多试一次；展示与留痕仍用原文。守卫：剥完不足
	 * 2 个字符（名字本身是个规格串）或没有汉字时保留原样，不产出空变体。
	 */
	public static List<String> nameVariants(String name) {
		String base = name == null ? "" : name.strip();
		if (base.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> variants = new LinkedHashSet<>();
		variants.add(base);
		String withoutAdjective = stripQualityAdjectives(base);
		variants.add(withoutAdjective);
		String withoutSpec = stripTailSpec(base);
		variants.add(withoutSpec);
		variants.add(stripQualityAdjectives(withoutSpec));
		return List.copyOf(variants);
	}

	public static String stripQualityAdjectives(String name) {
		String current = name == null ? "" : name.strip();
		while (true) {
			String stripped = TAIL_ADJECTIVE.matcher(current).replaceFirst("").strip();
			stripped = HEAD_ADJECTIVE.matcher(stripped).replaceFirst("").strip();
			if (stripped.equals(current) || stripped.length() < 2) {
				return current;
			}
			current = stripped;
		}
	}

	public static String stripTailSpec(String name) {
		String current = name == null ? "" : name.strip();
		while (true) {
			Matcher matcher = TAIL_SPEC.matcher(current);
			String stripped = current;
			if (matcher.find() && matcher.start() > 0) {
				stripped = TAIL_DEBRIS.matcher(current.substring(0, matcher.start())).replaceFirst("").strip();
			}
			if (stripped.equals(current) || stripped.length() < 2) {
				return current;
			}
			current = stripped;
		}
	}

	/** 品名里的去重汉字（按出现序）——单字兜底检索的召回粒度 */
	public static List<String> distinctCjkChars(String name) {
		String normalized = normalizeName(name);
		LinkedHashSet<String> chars = new LinkedHashSet<>();
		normalized.codePoints().forEach(codePoint -> {
			String character = new String(Character.toChars(codePoint));
			if (character.matches("[\\u4e00-\\u9fa5]")) {
				chars.add(character);
			}
		});
		return List.copyOf(chars);
	}

}
