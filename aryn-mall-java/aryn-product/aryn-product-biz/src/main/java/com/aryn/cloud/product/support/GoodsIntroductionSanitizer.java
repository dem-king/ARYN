package com.aryn.cloud.product.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品卖点（商详首屏富文本片段）的白名单清洗。
 *
 * <p>卖点由运营在后台富文本编辑器录入，允许逐商品配色（span 的 color 等），
 * 但首屏只有一段排版空间：只放行段落/换行/行内强调标签，样式只保留
 * color/font-weight/font-size/text-align；其余标签剥壳留文字，
 * 脚本/样式/图片/表格整段丢弃，避免运营把首屏改成第二个图文详情。</p>
 *
 * <p>清洗是写入口径（saveGoods/updateGoods）：库里只存白名单内的 HTML，
 * C 端与管后台读到的都是同一份已清洗内容，渲染侧不需要重复过滤。</p>
 */
public final class GoodsIntroductionSanitizer {

	/** 可见字数上限：一行 20 字左右，6 行以内，避免首屏被卖点挤占 */
	public static final int MAX_VISIBLE_LENGTH = 120;

	/** 清洗后 HTML 长度上限：配色/加粗会放大 2~3 倍，留足余量 */
	public static final int MAX_HTML_LENGTH = 4000;

	private static final Set<String> ALLOWED_TAGS = Set.of("p", "br", "span", "strong", "em", "u", "s");

	private static final Set<String> ALLOWED_STYLES = Set.of("color", "font-weight", "font-size", "text-align");

	private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

	private static final Pattern DROP_BLOCK = Pattern.compile(
			"(?is)<(script|style|iframe|object|embed|template|noscript)\\b[^>]*>.*?</\\1\\s*>");

	/** 未闭合的危险起始标签：直接截断其后内容，避免脚本体以纯文本形式外露 */
	private static final Pattern UNCLOSED_DROP_START = Pattern.compile(
			"(?i)<(script|style|iframe|object|embed|template|noscript)\\b");

	private static final Pattern TAG = Pattern.compile(
			"<\\s*(/?)\\s*([a-zA-Z][a-zA-Z0-9]*)((?:\"[^\"]*\"|'[^']*'|[^>\"'])*)>");

	private static final Pattern STYLE_ATTR = Pattern.compile(
			"(?i)\\bstyle\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

	private static final Pattern HTML_TAG = Pattern.compile("(?s)<[^>]*>");

	private static final Pattern ENTITY = Pattern.compile("&(?:[a-zA-Z]+|#\\d+|#x[0-9a-fA-F]+);");

	private static final Pattern COLOR = Pattern.compile(
			"(?i)^(#[0-9a-f]{3,8}"
					+ "|rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*(,\\s*(?:0|1|0?\\.\\d+)\\s*)?\\)"
					+ "|[a-z]{3,20})$");

	private static final Pattern FONT_WEIGHT = Pattern.compile("(?i)^(normal|bold|bolder|lighter|[1-9]00)$");

	private static final Pattern FONT_SIZE = Pattern.compile("(?i)^\\d{1,3}(px|rpx|em|rem|%)?$");

	private static final Pattern TEXT_ALIGN = Pattern.compile("(?i)^(left|right|center|justify)$");

	private GoodsIntroductionSanitizer() {
	}

	/**
	 * 清洗卖点 HTML。null/空串返回 null（管理端局部更新时 MyBatis-Plus 会忽略 null，
	 * 保留库里既有卖点）；清洗后无可见文字同样返回 null，整块不渲染。
	 */
	public static String sanitize(String html) {
		if (!StringUtils.hasText(html)) {
			return null;
		}
		String cleaned = COMMENT.matcher(html).replaceAll("");
		cleaned = DROP_BLOCK.matcher(cleaned).replaceAll("");
		Matcher unclosed = UNCLOSED_DROP_START.matcher(cleaned);
		if (unclosed.find()) {
			cleaned = cleaned.substring(0, unclosed.start());
		}

		Matcher matcher = TAG.matcher(cleaned);
		StringBuilder out = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			out.append(cleaned, last, matcher.start());
			appendTag(out, !matcher.group(1).isEmpty(),
					matcher.group(2).toLowerCase(Locale.ROOT), matcher.group(3));
			last = matcher.end();
		}
		out.append(cleaned.substring(last));

		String result = out.toString().trim();
		String visible = visibleText(result);
		if (visible.isEmpty()) {
			return null;
		}
		if (visible.length() > MAX_VISIBLE_LENGTH) {
			throw new ArynBusinessException("商品卖点不能超过 " + MAX_VISIBLE_LENGTH + " 个字");
		}
		if (result.length() > MAX_HTML_LENGTH) {
			throw new ArynBusinessException("商品卖点内容过长，请精简排版");
		}
		return result;
	}

	private static void appendTag(StringBuilder out, boolean closing, String name, String attributes) {
		if (!ALLOWED_TAGS.contains(name)) {
			// 剥壳留文字：标题/列表/链接等首屏用不到的标签，保留其文本内容
			return;
		}
		if ("br".equals(name)) {
			if (!closing) {
				out.append("<br/>");
			}
			return;
		}
		if (closing) {
			out.append("</").append(name).append('>');
			return;
		}
		out.append('<').append(name);
		if ("span".equals(name)) {
			String style = filterStyle(attributes);
			if (!style.isEmpty()) {
				out.append(" style=\"").append(style).append('"');
			}
		}
		out.append('>');
	}

	private static String filterStyle(String attributes) {
		Matcher matcher = STYLE_ATTR.matcher(attributes == null ? "" : attributes);
		if (!matcher.find()) {
			return "";
		}
		String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		List<String> kept = new ArrayList<>();
		for (String declaration : raw.split(";")) {
			int colon = declaration.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String name = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			String value = declaration.substring(colon + 1).trim();
			if (!ALLOWED_STYLES.contains(name) || !isAllowedStyleValue(name, value)) {
				continue;
			}
			kept.add(name + ": " + value);
		}
		return String.join(";", kept);
	}

	private static boolean isAllowedStyleValue(String name, String value) {
		if (value.isEmpty() || value.length() > 32) {
			return false;
		}
		return switch (name) {
			case "color" -> COLOR.matcher(value).matches();
			case "font-weight" -> FONT_WEIGHT.matcher(value).matches();
			case "font-size" -> FONT_SIZE.matcher(value).matches();
			case "text-align" -> TEXT_ALIGN.matcher(value).matches();
			default -> false;
		};
	}

	private static String visibleText(String html) {
		String text = HTML_TAG.matcher(html).replaceAll("");
		text = ENTITY.matcher(text).replaceAll("x");
		return text.replaceAll("\\s+", " ").trim();
	}
}
