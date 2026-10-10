package com.aryn.cloud.order.support;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信群接龙报货文本解析器（纯函数）。
 *
 * <p>把船员在微信群里接龙的自由文本解析成「人 × 商品 × 数量」：
 *
 * <pre>
 * 1. 任化东 红富士冰糖心4斤，香蕉（熟点）3斤，阳光玫瑰1串
 * 2. 汤伟杰 青岛啤酒（冰淳）500ML12罐 2箱
 * 3. 水手长 盐汽水四包
 * </pre>
 *
 * <p>解析策略是「尽力而为 + 留痕」：规则能覆盖大多数行（序号剥离、人名提取、
 * 中英文数字数量、连写拆分），解析不准的行不丢弃也不猜——数量缺失、人名缺失
 * 都通过 {@code warning} 如实上报，由导入报告的人工核对兜底。与
 * {@link ReplenishImportExcel} 的定位一致：人工永远以服务端落库的解析行为准，
 * 解析器只负责把原文切成可核对的行。
 *
 * <p>人名支持两种排版：同行紧跟（「任化东 红富士4斤」）与先报名后换行报货——
 * 后者靠「人名暂存」接续：纯人名行暂存起来，紧随其后的报货行（无更多人名）
 * 都归属该人，直到下一个人名出现。
 *
 * <p>已知留白（刻意不猜，交给报告页人工修正）：
 * <ul>
 *   <li>「红和黄 各一斤」拆不出两个商品——数量只出现一次，作为一行处理，
 *       品名去掉「各」并提示核对数量口径；</li>
 *   <li>纯职务称呼（水手长/三管）解析为人名原文，与真实成员的关联由确认阶段
 *       按成员自填姓名精确匹配，匹配不上就作为归属标签原样保留；</li>
 *   <li>小数/「半斤」这类非整数数量不猜进位，置空并提示人工调整。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/10/11
 */
public final class ChainOrderTextParser {

	/** 行首序号：「1.」「1、」「1：」「1)」「（1）」「①」 */
	private static final Pattern LIST_MARKER = Pattern
		.compile("^\\s*(\\d{1,3}\\s*[.、:：)）]|[（(]\\d{1,3}[)）]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\\s*");

	/**
	 * 数量标记：数字（含中文数字）+ 采购单位。
	 *
	 * <p>单位表只收「报货口语里当数量用的单位」。规格里的数字（600ml、500ML）
	 * 因单位不在表内天然不成为标记；「24瓶4箱」「24罐装一箱」这类连写由
	 * {@link #mergeRunOn} 兜回。
	 */
	private static final Pattern QTY_TOKEN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?|[一二两三四五六七八九十]{1,3})\\s*"
			+ "(公斤|千克|[kK][gG]|斤|箱|包|瓶|罐|串|个|盒|袋|桶|件|听|支|把|份|条|只|张|卷|块|提|扎|双|副)");

	/** 段落分隔：逗号/顿号/分号/句号。空格不是分隔符（品名内含空格），靠数量标记定界 */
	private static final Pattern SEGMENT_SPLIT = Pattern.compile("[，、,；;。]");

	/** 行内人名与商品的分隔符 */
	private static final Pattern PERSON_SPLIT = Pattern.compile("[\\s\\u3000，、,；;。：:]");

	private static final Pattern HAS_CJK = Pattern.compile("[\\u4e00-\\u9fa5]");

	/** 人名长度上限：超过按「没有报人名」处理，避免把长品名误切成人名 */
	private static final int MAX_PERSON_LENGTH = 12;

	/** 中文数字位值表（「两」按 2 计） */
	private static final Map<Character, Integer> CN_DIGITS = Map.of('一', 1, '二', 2, '两', 2, '三', 3, '四', 4,
			'五', 5, '六', 6, '七', 7, '八', 8, '九', 9);

	private ChainOrderTextParser() {
	}

	/**
	 * 解析出的单个报货项。
	 *
	 * @param lineNo 源文本行号（从 1 开始）
	 * @param personName 人名原文；未识别到时为 null（报告里提示人工指定归属）
	 * @param goodsName 商品名原文（数量标记之前的部分）
	 * @param quantity 解析出的数量；无法解析（小数/缺失）时为 null
	 * @param unit 数量单位原文（斤/箱/包…），仅供报告展示，不入购物车
	 * @param rawSegment 该项所在的原始文本段（留痕，便于核对「为什么这么解析」）
	 * @param warning 解析疑点说明；无疑点时为 null
	 */
	public record ParsedItem(int lineNo, String personName, String goodsName, Integer quantity, String unit,
			String rawSegment, String warning) {
	}

	/** 数量标记：在段内的位置 + 数字原文 + 单位 */
	private record Token(int start, int end, String numberText, String unit) {

		/** 数量标记原文（数字+单位），连写合并时拼回品名用 */
		String text() {
			return numberText + unit;
		}
	}

	/** 解析中间态：品名 + 数量标记（无标记的尾部行 token 为 null） */
	private static final class RawItem {

		private String name;
		private Token token;
		private Integer quantity;
		private String warning;

		private RawItem(String name, Token token) {
			this.name = name;
			this.token = token;
			if (token == null) {
				this.quantity = null;
				this.warning = "未识别到数量，请人工补填";
				return;
			}
			this.quantity = parseQuantity(token.numberText());
			if (this.quantity == null) {
				this.warning = "数量「" + token.numberText() + "（" + token.unit() + "）」无法解析，请人工调整";
			}
		}
	}

	/**
	 * 解析接龙全文。
	 *
	 * @param text 用户粘贴的接龙原文
	 * @return 解析出的报货项列表；一行可以产出多个项，解析不出任何项时返回空列表
	 */
	public static List<ParsedItem> parse(String text) {
		List<ParsedItem> items = new ArrayList<>();
		if (!StringUtils.hasText(text)) {
			return items;
		}
		String[] lines = text.replace("\\u3000", " ").split("\\r?\\n");
		String[] stripped = new String[lines.length];
		// 「人员名单格式」判定：出现序号标记或「人名+商品」同行，说明整段是
		// 按人接龙的排版——纯人名行才能当人名、报货行才能归属上一个人名。
		// 整段都没有这类标记时（纯购物清单），裸短行一律按商品处理，
		// 否则「土豆」这种没填数量的商品会被误认成人名、吞掉后面几行的归属。
		boolean namedFormat = false;
		for (int index = 0; index < lines.length; index++) {
			stripped[index] = stripListMarker(lines[index]);
			if (!StringUtils.hasText(stripped[index])) {
				continue;
			}
			if (hasListMarker(lines[index]) || extractPerson(stripped[index]) != null) {
				namedFormat = true;
			}
		}
		if (!namedFormat) {
			for (int index = 0; index < lines.length; index++) {
				if (StringUtils.hasText(stripped[index])) {
					parseGoods(index + 1, null, stripped[index], items);
				}
			}
			return items;
		}

		// 人名暂存：纯人名行记下来，紧随其后的报货行（没有再报人名）都归属该人
		String pendingPerson = null;
		for (int index = 0; index < lines.length; index++) {
			if (!StringUtils.hasText(stripped[index])) {
				continue;
			}
			pendingPerson = parseLine(index + 1, stripped[index], nextNonBlank(stripped, index), pendingPerson, items);
		}
		return items;
	}

	/** 剥离行首序号标记；无标记原样返回（trim 过的） */
	private static String stripListMarker(String line) {
		String trimmed = line.strip();
		Matcher matcher = LIST_MARKER.matcher(trimmed);
		if (!matcher.find()) {
			return trimmed;
		}
		return trimmed.substring(matcher.end()).strip();
	}

	/** 行首是否有序号标记（与 {@link #stripListMarker} 同一判定） */
	private static boolean hasListMarker(String line) {
		return LIST_MARKER.matcher(line.strip()).find();
	}

	/** 取 stripped 数组中 from 之后第一个非空行；没有则 null */
	private static String nextNonBlank(String[] stripped, int from) {
		for (int index = from + 1; index < stripped.length; index++) {
			if (StringUtils.hasText(stripped[index])) {
				return stripped[index];
			}
		}
		return null;
	}

	/**
	 * 解析单行，返回该行之后生效的「人名暂存」。
	 *
	 * <p>判定顺序：行首人名 → 无分隔/首段含数量的行看整行——
	 * 含数量标记即为报货行（归属暂存人名），不含且长度像人名则更新暂存。
	 */
	private static String parseLine(int lineNo, String line, String nextLine, String pendingPerson,
			List<ParsedItem> items) {
		String person = extractPerson(line);
		if (person != null) {
			parseGoods(lineNo, person, line.substring(person.length()), items);
			return person;
		}
		if (findQuantityToken(line) != null) {
			// 报货行：归属暂存人名（可能是前一行报名、本行报货的排版）
			parseGoods(lineNo, pendingPerson, line, items);
			return pendingPerson;
		}
		// 纯人名行：仅当下一行不是「新人名行」时才当人名——
		// 否则孤儿短行（漏写数量的商品）会抢走后面几行的归属
		if (line.length() <= MAX_PERSON_LENGTH && (nextLine == null || extractPerson(nextLine) == null)) {
			return line;
		}
		// 长文本且无数量标记：当商品行处理（数量缺失在行内提示）
		parseGoods(lineNo, pendingPerson, line, items);
		return pendingPerson;
	}

	/** 解析人名之后的报货正文，按标点切段、段内按数量标记定界 */
	private static void parseGoods(int lineNo, String person, String goodsText, List<ParsedItem> items) {
		String goods = goodsText.replaceFirst("^[\\s\\u3000，、,；;。：:]+", "");
		if (!StringUtils.hasText(goods)) {
			// 整行只有人名没有报货内容：不产出行，也不算错误（有人只报名不报货）
			return;
		}
		for (String segment : SEGMENT_SPLIT.split(goods)) {
			String trimmed = segment.strip();
			if (trimmed.isEmpty()) {
				continue;
			}
			parseSegment(lineNo, person, trimmed, items);
		}
	}

	/**
	 * 行首人名提取：取第一个分隔符之前的文本，且不含数量标记（含数量说明它是商品）。
	 *
	 * <p>「任化东 红富士冰糖心4斤」「机工卜兆土 香蕉3斤」「水手长：盐汽水四包」
	 * 都能正确切出人名；「香蕉3斤，苹果2斤」首段含数量，判定为没有报人名的行。
	 */
	private static String extractPerson(String line) {
		Matcher separator = PERSON_SPLIT.matcher(line);
		if (!separator.find()) {
			return null;
		}
		String candidate = line.substring(0, separator.start()).strip();
		if (candidate.isEmpty() || candidate.length() > MAX_PERSON_LENGTH) {
			return null;
		}
		if (findQuantityToken(candidate) != null) {
			return null;
		}
		return candidate;
	}

	private static void parseSegment(int lineNo, String person, String segment, List<ParsedItem> items) {
		List<Token> tokens = scanQuantityTokens(segment);
		if (tokens.isEmpty()) {
			items.add(new ParsedItem(lineNo, person, segment, null, null, segment, "未识别到数量，请人工补填"));
			return;
		}

		// 数量标记定界：相邻两个标记之间的文本是前一个商品的品名
		List<RawItem> rawItems = new ArrayList<>(tokens.size());
		int nameStart = 0;
		for (Token token : tokens) {
			String name = segment.substring(nameStart, token.start()).strip();
			if (!rawItems.isEmpty() && isParen(segment.charAt(nameStart))) {
				// 紧贴上一个单位后面的括号组是上一个商品的规格（「香蕉3斤（青） 蒜蓉辣椒酱2瓶」）；
				// 带空格隔开的括号是本商品名的一部分（「3斤 （青）苹果2斤」的「（青）苹果」），不动
				name = moveLeadingParenGroups(name, rawItems.get(rawItems.size() - 1));
			}
			rawItems.add(new RawItem(name, token));
			nameStart = token.end();
		}
		appendTrailingText(segment, nameStart, rawItems);

		mergeRunOn(rawItems);
		for (RawItem rawItem : rawItems) {
			items.add(new ParsedItem(lineNo, person, polishName(rawItem), rawItem.quantity,
					rawItem.token == null ? null : rawItem.token.unit(), segment, rawItem.warning));
		}
	}

	/**
	 * 品名收尾：去掉结尾的「各」（「红和黄 各一斤」）并提示数量口径，
	 * 其余原文保留——口语品名再怪也是staff核对时的唯一线索。
	 */
	private static String polishName(RawItem rawItem) {
		String name = rawItem.name == null ? "" : rawItem.name.strip();
		if (name.endsWith("各")) {
			String polished = name.substring(0, name.length() - 1).strip();
			if (rawItem.warning == null) {
				rawItem.warning = "原文含「各」，多项共用该数量，请核对";
			}
			return polished;
		}
		return name;
	}

	private static boolean isParen(char character) {
		return character == '（' || character == '(';
	}

	/**
	 * 把品名开头的连续括号组移交给上一个商品（规格后缀），返回剩余品名。
	 */
	private static String moveLeadingParenGroups(String name, RawItem previous) {
		String rest = name;
		while (isParen(rest.charAt(0))) {
			int close = rest.indexOf(rest.charAt(0) == '（' ? '）' : ')');
			if (close < 0) {
				break;
			}
			previous.name = previous.name + rest.substring(0, close + 1);
			rest = rest.substring(close + 1).strip();
			if (rest.isEmpty()) {
				break;
			}
		}
		return rest;
	}

	private static List<Token> scanQuantityTokens(String segment) {
		List<Token> tokens = new ArrayList<>();
		Matcher matcher = QTY_TOKEN.matcher(segment);
		while (matcher.find()) {
			tokens.add(new Token(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)));
		}
		return tokens;
	}

	private static Token findQuantityToken(String text) {
		Matcher matcher = QTY_TOKEN.matcher(text);
		return matcher.find() ? new Token(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)) : null;
	}

	/**
	 * 尾部文本归属：括号规格（「香蕉3斤（青）」）拼回最后一个商品；
	 * 单位后缀（「24罐装」的「装」）同理；其余尾部文本单独成行并提示数量缺失。
	 */
	private static void appendTrailingText(String segment, int tailStart, List<RawItem> rawItems) {
		if (tailStart >= segment.length() || rawItems.isEmpty()) {
			return;
		}
		String tail = segment.substring(tailStart).strip();
		if (tail.isEmpty()) {
			return;
		}
		RawItem last = rawItems.get(rawItems.size() - 1);
		if (tail.matches("^[（(].*[）)]$") || tail.matches("^[装支把份只]{1,2}$")) {
			last.name = last.name + tail;
			return;
		}
		rawItems.add(new RawItem(tail.replaceFirst("^[\\s\\u3000，、,；;。：:]+", ""), null));
	}

	/**
	 * 连写合并：两个数量标记紧挨着、后者前面没有品名（或只有「装」这类规格后缀）时，
	 * 前一个标记其实写在规格里——「600ml*24瓶4箱」要买的是 4 箱，
	 * 「青岛清爽24罐装一箱」要买的是 1 箱青岛清爽（规格 24 罐装）。
	 *
	 * <p>从后往前合并：后一个数量覆盖前一个，前一个标记原文拼回品名。
	 */
	private static void mergeRunOn(List<RawItem> rawItems) {
		for (int index = rawItems.size() - 1; index > 0; index--) {
			RawItem current = rawItems.get(index);
			if (current.token == null) {
				continue;
			}
			String name = current.name == null ? "" : current.name;
			boolean nameless = name.isEmpty() || !HAS_CJK.matcher(name).find();
			if (!nameless && !"装".equals(name)) {
				continue;
			}
			RawItem previous = rawItems.get(index - 1);
			if (previous.token == null) {
				continue;
			}
			previous.name = (previous.name == null ? "" : previous.name) + previous.token.text()
					+ ("装".equals(name) ? name : "");
			previous.token = current.token;
			previous.quantity = current.quantity;
			previous.warning = current.warning;
			rawItems.remove(index);
		}
	}

	/** 数量文本转整数：阿拉伯数字（小数拒绝）、中文数字（一~九十九） */
	private static Integer parseQuantity(String numberText) {
		String text = numberText.strip();
		if (text.matches("[0-9]+")) {
			try {
				return Integer.valueOf(text);
			}
			catch (NumberFormatException exception) {
				return null;
			}
		}
		if (text.matches("[0-9]+\\.[0-9]+")) {
			return null;
		}
		return chineseQuantity(text);
	}

	private static Integer chineseQuantity(String text) {
		int value = 0;
		boolean afterTen = false;
		int pendingDigit = 0;
		for (char character : text.toCharArray()) {
			if (character == '十') {
				int leading = pendingDigit > 0 ? pendingDigit : 1;
				value += leading * 10;
				pendingDigit = 0;
				afterTen = true;
				continue;
			}
			Integer digit = CN_DIGITS.get(character);
			if (digit == null) {
				return null;
			}
			if (afterTen) {
				value += digit;
				afterTen = false;
			}
			else {
				pendingDigit = digit;
			}
		}
		if (pendingDigit > 0) {
			if (afterTen) {
				return null;
			}
			value += pendingDigit;
		}
		return value > 0 ? value : null;
	}

}
