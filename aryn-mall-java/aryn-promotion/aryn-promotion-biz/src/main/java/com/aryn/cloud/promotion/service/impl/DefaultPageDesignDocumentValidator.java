package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.api.constant.PageDesignComponentTypes;
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 默认页面装修文档校验器。
 * <p>
 * 兼容 schema v2（components 平铺）与 v3（sections 区块嵌套）；
 * 未知组件、缺失组件标识、非法跳转链接、空手选数据源为阻断错误；
 * 组件数量、图片数量、页面体积、请求数超预算为警告。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Component
public class DefaultPageDesignDocumentValidator implements PageDesignDocumentValidator {

	private static final int SCHEMA_VERSION_V2 = 2;

	private static final int SCHEMA_VERSION_V3 = 3;

	private static final int MAX_COMPONENTS = 50;

	private static final int MAX_IMAGES = 30;

	private static final int MAX_REQUESTS = 10;

	private static final long MAX_CONTENT_BYTES = 256L * 1024L;

	private static final Set<String> LINK_TYPES = Set.of("activity", "category", "coupon", "custom",
			"customer-service", "goods", "mini-program", "page");

	private static final Set<String> LINK_TARGET_TYPES = Set.of("activity", "category", "coupon", "goods", "page");

	private static final Set<String> LINK_PATH_TYPES = Set.of("custom", "mini-program");

	private static final String MANUAL_MODE = "manual";

	private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg",
			".avif");

	private static final Set<String> IMAGE_KEY_WORDS = Set.of("img", "image", "pic", "icon", "logo", "avatar",
			"banner", "bg", "background", "poster", "cover", "photo", "thumb");

	/**
	 * 自定义 HTML 危险特征（小写匹配）。命中任一即阻断发布。
	 * <p>
	 * 项目未引入 jsoup / OWASP HTML Sanitizer，故采用「阻断危险内容」而非「白名单净化」：
	 * 运营只能粘贴 div/span/p/a/img/表格/标题/列表等展示型标签，脚本、事件属性、
	 * 框架、表单、object/embed/link/meta/base、伪协议一律拒绝。
	 */
	private static final Map<String, String> CUSTOM_HTML_DANGER_MARKERS = Map.ofEntries(
			Map.entry("<script", "script 标签"),
			Map.entry("<iframe", "iframe 标签"),
			Map.entry("<form", "form 标签"),
			Map.entry("<object", "object 标签"),
			Map.entry("<embed", "embed 标签"),
			Map.entry("<link", "link 标签"),
			Map.entry("<meta", "meta 标签"),
			Map.entry("<base", "base 标签"),
			Map.entry("<svg", "svg 标签"),
			Map.entry("<template", "template 标签"),
			Map.entry("javascript:", "javascript: 伪协议"),
			Map.entry("vbscript:", "vbscript: 伪协议"),
			Map.entry("data:text/html", "data:text/html 伪协议"),
			Map.entry("eval(", "eval() 调用"),
			Map.entry("expression(", "CSS expression() 调用"));

	private static final java.util.regex.Pattern EVENT_HANDLER_PATTERN = java.util.regex.Pattern
		.compile("\\bon\\w+\\s*=");

	/**
	 * 数字字符引用（十进制 `&#106;` 与十六进制 `&#x6A;`，分号可省略）。
	 */
	private static final java.util.regex.Pattern NUMERIC_ENTITY_PATTERN = java.util.regex.Pattern
		.compile("&#(x[0-9a-f]+|[0-9]+);?");

	/**
	 * 常见具名字符引用。
	 * <p>
	 * 只覆盖能拼出危险特征的那部分（尖括号、引号、冒号、空白）——不做完整 HTML 实体表，
	 * 因为这里的目的不是「还原文本」而是「不让实体编码成为绕过黑名单的通道」。
	 */
	private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
			Map.entry("&lt;", "<"),
			Map.entry("&gt;", ">"),
			Map.entry("&amp;", "&"),
			Map.entry("&quot;", "\""),
			Map.entry("&apos;", "'"),
			Map.entry("&colon;", ":"),
			Map.entry("&tab;", "\t"),
			Map.entry("&newline;", "\n"),
			Map.entry("&sol;", "/"),
			Map.entry("&lpar;", "("),
			Map.entry("&rpar;", ")"));

	/**
	 * 解码 HTML 字符引用，用于让黑名单匹配看见「浏览器实际会解析出的内容」。
	 * <p>
	 * 不解码时 `&lt;script` / `&#106;avascript:` / `&lt;img onerror=` 都能绕过纯子串匹配，
	 * 而浏览器会照常还原成危险标签或伪协议。
	 * <p>
	 * 反复解码若干轮以覆盖多重编码（如 `&amp;#106;`）；轮数上限保证不会在自引用实体上打转。
	 */
	private static String decodeHtmlEntities(String value) {
		if (value.indexOf('&') < 0) {
			return value;
		}
		String decoded = value;
		for (int round = 0; round < 3; round++) {
			String next = replaceNamedEntities(replaceNumericEntities(decoded));
			if (next.equals(decoded)) {
				break;
			}
			decoded = next;
		}
		return decoded;
	}

	private static String replaceNumericEntities(String value) {
		java.util.regex.Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(value);
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			String digits = matcher.group(1);
			int codePoint;
			try {
				codePoint = digits.startsWith("x") || digits.startsWith("X")
						? Integer.parseInt(digits.substring(1), 16)
						: Integer.parseInt(digits);
			}
			catch (NumberFormatException ex) {
				continue;
			}
			// 用 appendReplacement 会引入分组转义问题，这里直接拼接并跳过非法码点
			if (!Character.isValidCodePoint(codePoint)) {
				continue;
			}
			matcher.appendReplacement(builder,
					java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
		}
		matcher.appendTail(builder);
		return builder.toString();
	}

	private static String replaceNamedEntities(String value) {
		String result = value;
		for (Map.Entry<String, String> entity : NAMED_ENTITIES.entrySet()) {
			if (result.contains(entity.getKey())) {
				result = result.replace(entity.getKey(), entity.getValue());
			}
		}
		return result;
	}

	/**
	 * 去掉空白与控制字符，供第二遍匹配使用。
	 * <p>
	 * 浏览器解析 URL 时会先剔除 tab/换行/回车，所以 `java\nscript:` 实际会照常执行；
	 * 事件属性名里插空白（`on\nerror=`）同理。压缩后再匹配一次即可覆盖这类绕过，
	 * 代价是极少数「文本里恰好拼出危险词」的内容会被保守拦下——对阻断式校验可接受。
	 */
	private static String compactForSafetyCheck(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (Character.isWhitespace(current) || Character.isISOControl(current)) {
				continue;
			}
			builder.append(current);
		}
		return builder.toString();
	}

	@Override
	public List<String> validate(String pageContent, String pageType) {
		return validateStructured(pageContent, pageType).getErrors().stream()
			.map(PageDesignValidationVO.Issue::getMessage)
			.toList();
	}

	@Override
	public PageDesignValidationVO validateStructured(String pageContent, String pageType) {
		PageDesignValidationVO result = new PageDesignValidationVO();
		JSONObject document = tryParse(pageContent);
		if (document == null) {
			result.getErrors().add(issue("CONTENT_INVALID", "页面内容不是有效JSON", null, null, null));
			return result;
		}
		int schemaVersion = document.getIntValue("schemaVersion");
		if (schemaVersion != SCHEMA_VERSION_V2 && schemaVersion != SCHEMA_VERSION_V3) {
			result.getErrors().add(issue("SCHEMA_VERSION", "schemaVersion必须为2或3", null, null, "schemaVersion"));
		}
		List<ComponentEntry> components = collectComponents(document, schemaVersion, result);
		validateComponentsAllowedForPageType(components, pageType, result);
		validateSingletonComponents(components, result);
		int imageCount = 0;
		int requestCount = 0;
		for (ComponentEntry entry : components) {
			ValidationContext context = validateComponent(entry, result);
			imageCount += context.imageCount;
			requestCount += context.requestCount;
		}
		PageDesignValidationVO.Budget budget = result.getPerformance();
		budget.setComponentCount(components.size());
		budget.setImageCount(imageCount);
		budget.setRequestCount(requestCount);
		budget.setContentBytes(pageContent == null ? 0 : pageContent.getBytes().length);
		appendBudgetWarnings(result);
		return result;
	}

	/**
	 * 校验「该页面类型是否允许放置这些组件」。
	 * <p>
	 * 管理端组件面板已按同一份白名单过滤，但面板只是编辑体验——直接构造
	 * payload 调接口即可绕过，因此发布前必须再拦一次。否则「商详页塞底部导航」
	 * 这类非法组合会一路发布上线，C 端渲染出来就是布局错乱。
	 * <p>
	 * 微页面(0)/首页(1)不做限制（{@code allowedTypesForPageType} 返回 {@code null}）：
	 * 它们是全功能页面，组件面板也不过滤。
	 */
	private void validateComponentsAllowedForPageType(List<ComponentEntry> components, String pageType,
			PageDesignValidationVO result) {
		Set<String> allowed = PageDesignComponentTypes.allowedTypesForPageType(pageType);
		if (allowed == null) {
			return;
		}
		for (ComponentEntry entry : components) {
			String type = entry.component().getString("type");
			if (type == null || allowed.contains(type)) {
				continue;
			}
			result.getErrors()
				.add(issue("COMPONENT_NOT_ALLOWED",
						"「" + pageTypeLabel(pageType) + "」不支持组件「" + type + "」",
						entry.component().getString("id"), type, entry.path() + ".type"));
		}
	}

	/**
	 * 页面类型的中文名，仅用于错误提示。
	 */
	private String pageTypeLabel(String pageType) {
		if (pageType == null) {
			return "该页面";
		}
		return switch (pageType) {
			case PageDesignComponentTypes.PAGE_TYPE_DETAIL -> "商品详情页";
			case PageDesignComponentTypes.PAGE_TYPE_CATEGORY -> "分类页";
			case PageDesignComponentTypes.PAGE_TYPE_USER_CENTER -> "个人中心页";
			case PageDesignComponentTypes.PAGE_TYPE_HOME -> "商城首页";
			case PageDesignComponentTypes.PAGE_TYPE_MICRO -> "微页面";
			default -> "页面类型" + pageType;
		};
	}

	/**
	 * 校验「全页面唯一」组件。
	 * <p>
	 * 船舶工作台读取的是当前用户此刻的船舶与靠港，同页出现多个只会互相矛盾，
	 * 因此重复出现直接发布阻断，而不是留给用户去猜哪个生效。
	 */
	private void validateSingletonComponents(List<ComponentEntry> components, PageDesignValidationVO result) {
		Map<String, Integer> seen = new HashMap<>();
		for (ComponentEntry entry : components) {
			String type = entry.component().getString("type");
			if (type == null || !PageDesignComponentTypes.SINGLETON_TYPES.contains(type)) {
				continue;
			}
			int count = seen.merge(type, 1, Integer::sum);
			if (count > 1) {
				result.getErrors()
					.add(issue("COMPONENT_DUPLICATED", "同一页面最多只能放置一个「" + singletonLabel(type) + "」",
							entry.component().getString("id"), type, entry.path() + ".type"));
			}
		}
	}

	/**
	 * 单例组件的用户可读名称。
	 * <p>
	 * 历史实现把「船舶工作台」写死在报错文案里，新增第二个单例组件后
	 * 配了两次补给单也只会提示「船舶工作台」，运营无从判断是哪个组件重复。
	 */
	private String singletonLabel(String type) {
		if (PageDesignComponentTypes.REPLENISH_CARD.equals(type)) {
			return "补给单卡片";
		}
		return "船舶工作台";
	}

	private JSONObject tryParse(String pageContent) {
		if (!StringUtils.hasText(pageContent)) {
			return null;
		}
		try {
			return JSONObject.parseObject(pageContent);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private List<ComponentEntry> collectComponents(JSONObject document, int schemaVersion,
			PageDesignValidationVO result) {
		List<ComponentEntry> components = new ArrayList<>();
		if (schemaVersion == SCHEMA_VERSION_V3) {
			JSONArray sections = document.getJSONArray("sections");
			if (sections == null || sections.isEmpty()) {
				result.getErrors().add(issue("SECTION_INVALID", "sections必须为非空数组", null, null, "sections"));
				return components;
			}
			for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
				JSONObject section = sections.getJSONObject(sectionIndex);
				String sectionPath = "sections[" + sectionIndex + "]";
				if (section == null || !StringUtils.hasText(section.getString("id"))) {
					result.getErrors().add(issue("SECTION_INVALID", "区块缺少id", null, null, sectionPath + ".id"));
					continue;
				}
				validateSectionCondition(section, sectionPath, result);
				collectFlatComponents(section.getJSONArray("components"), sectionPath + ".components", components);
			}
			return components;
		}
		collectFlatComponents(document.getJSONArray("components"), "components", components);
		if (components.isEmpty() && document.getJSONArray("components") == null) {
			result.getErrors().add(issue("COMPONENTS_INVALID", "components必须为数组", null, null, "components"));
		}
		return components;
	}

	/** 区块显示条件中「以 id 集合为目标」的规则类型 → 对应的 id 字段名 */
	private static final Map<String, String> CONDITION_RULE_ID_KEYS = Map.of(
			"memberLevel", "memberLevelIds",
			"userTag", "userTagIds");

	private static String conditionRuleLabel(String type) {
		return switch (type) {
			case "memberLevel" -> "会员等级";
			case "userTag" -> "用户标签";
			default -> type;
		};
	}

	/**
	 * 校验区块显示条件中的成员规则。
	 * <p>
	 * 「会员等级/用户标签」规则必须选定至少一个目标：编辑器添加规则时先落一条
	 * 空选项规则，若原样保存发布，C 端按「规则未命中」处理会让整个区块对所有人
	 * 不可见（真实事故：首页唯一区块挂着 {@code memberLevelIds: []}，整页只剩
	 * 导航条白屏）。字符串简写条件与空 rules 数组不在此拦截，由 C 端 fail-open。
	 */
	private void validateSectionCondition(JSONObject section, String sectionPath, PageDesignValidationVO result) {
		JSONObject style = section.getJSONObject("style");
		if (style == null) {
			return;
		}
		// 条件可能是字符串简写（login/guest/always），只有对象组合条件才需要逐条校验
		if (!(style.get("condition") instanceof JSONObject condition)) {
			return;
		}
		JSONArray rules = condition.getJSONArray("rules");
		if (rules == null) {
			return;
		}
		for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
			JSONObject rule = rules.getJSONObject(ruleIndex);
			if (rule == null) {
				continue;
			}
			String type = rule.getString("type");
			String idsKey = CONDITION_RULE_ID_KEYS.get(type);
			if (idsKey == null) {
				continue;
			}
			JSONArray ids = rule.getJSONArray(idsKey);
			if (ids == null || ids.isEmpty()) {
				result.getErrors()
					.add(issue("CONDITION_RULE_EMPTY",
							"区块显示条件的「" + conditionRuleLabel(type)
									+ "」规则未选择任何选项，该区块将对所有人不可见，请补选或删除该规则",
							section.getString("id"), null,
							sectionPath + ".style.condition.rules[" + ruleIndex + "]." + idsKey));
			}
		}
	}

	private void collectFlatComponents(JSONArray array, String basePath, List<ComponentEntry> components) {
		if (array == null) {
			return;
		}
		for (int index = 0; index < array.size(); index++) {
			JSONObject component = array.getJSONObject(index);
			if (component != null) {
				components.add(new ComponentEntry(component, basePath + "[" + index + "]"));
			}
		}
	}

	private ValidationContext validateComponent(ComponentEntry entry, PageDesignValidationVO result) {
		JSONObject component = entry.component();
		String componentId = component.getString("id");
		String componentType = component.getString("type");
		String basePath = entry.path();
		if (!StringUtils.hasText(componentId)) {
			result.getErrors().add(issue("COMPONENT_INVALID", "组件缺少id", null, componentType, basePath + ".id"));
		}
		if (!StringUtils.hasText(componentType)) {
			result.getErrors().add(issue("COMPONENT_INVALID", "组件缺少type", componentId, null, basePath + ".type"));
			return new ValidationContext(componentId, componentType);
		}
		if (!PageDesignComponentTypes.KNOWN_TYPES.contains(componentType)) {
			result.getErrors()
				.add(issue("COMPONENT_UNKNOWN", "未知组件类型：" + componentType + "，请删除该组件或升级编辑器", componentId,
						componentType, basePath + ".type"));
			return new ValidationContext(componentId, componentType);
		}
		JSONObject props = component.getJSONObject("props");
		if (props == null) {
			result.getErrors()
				.add(issue("PROPS_INVALID", "组件配置不能为空", componentId, componentType, basePath + ".props"));
			return new ValidationContext(componentId, componentType);
		}
		ValidationContext context = new ValidationContext(componentId, componentType);
		walk(props, basePath + ".props", context, result);
		validateDataSource(props, context, result);
		validateCustomHtml(props, componentId, componentType, basePath, result);
		result.getReferences().getGoodsIds().addAll(context.goodsIds);
		result.getReferences().getCouponIds().addAll(context.couponIds);
		result.getReferences().getActivityIds().addAll(context.activityIds);
		result.getReferences().getCategoryIds().addAll(context.categoryIds);
		return context;
	}

	private void walk(JSONObject current, String path, ValidationContext context, PageDesignValidationVO result) {
		for (String key : current.keySet()) {
			Object value = current.get(key);
			String fieldPath = path + "." + key;
			if (value instanceof JSONObject nested) {
				if (isDecorationLink(nested)) {
					validateLink(nested, fieldPath, context, result);
				}
				else {
					walk(nested, fieldPath, context, result);
				}
			}
			else if (value instanceof JSONArray array) {
				walkArray(array, fieldPath, context, result);
			}
			else if (value instanceof String text && isImageValue(key, text)) {
				context.imageCount++;
			}
		}
	}

	private void walkArray(JSONArray array, String path, ValidationContext context, PageDesignValidationVO result) {
		for (int index = 0; index < array.size(); index++) {
			Object value = array.get(index);
			String fieldPath = path + "[" + index + "]";
			if (value instanceof JSONObject nested) {
				if (isDecorationLink(nested)) {
					validateLink(nested, fieldPath, context, result);
				}
				else {
					walk(nested, fieldPath, context, result);
				}
			}
			else if (value instanceof JSONArray nestedArray) {
				walkArray(nestedArray, fieldPath, context, result);
			}
		}
	}

	private boolean isDecorationLink(JSONObject value) {
		String type = value.getString("type");
		return type != null && LINK_TYPES.contains(type) && value.containsKey("path");
	}

	private void validateLink(JSONObject link, String fieldPath, ValidationContext context,
			PageDesignValidationVO result) {
		String type = link.getString("type");
		String targetId = link.getString("targetId");
		if (LINK_TARGET_TYPES.contains(type)) {
			if (!StringUtils.hasText(targetId)) {
				result.getErrors()
					.add(issue("LINK_INVALID", "跳转链接缺少目标，请重新选择", context.componentId, context.componentType,
							fieldPath + ".targetId"));
			}
			else {
				switch (type) {
					case "goods" -> context.goodsIds.add(targetId);
					case "coupon" -> context.couponIds.add(targetId);
					case "activity" -> context.activityIds.add(targetId);
					case "category" -> context.categoryIds.add(targetId);
					default -> {
					}
				}
			}
		}
		if (LINK_PATH_TYPES.contains(type) && !StringUtils.hasText(link.getString("path"))) {
			result.getErrors()
				.add(issue("LINK_INVALID", "跳转链接缺少路径，请重新填写", context.componentId, context.componentType,
						fieldPath + ".path"));
		}
	}

	private void validateDataSource(JSONObject props, ValidationContext context, PageDesignValidationVO result) {
		JSONObject dataSource = props.getJSONObject("dataSource");
		if (dataSource == null) {
			return;
		}
		String mode = dataSource.getString("mode");
		JSONArray targetIds = dataSource.getJSONArray("targetIds");
		boolean hasTargets = targetIds != null && !targetIds.isEmpty();
		if (MANUAL_MODE.equals(mode) && PageDesignComponentTypes.DATA_DRIVEN_TYPES.contains(context.componentType)
				&& !hasTargets) {
			result.getErrors()
				.add(issue("DATASOURCE_EMPTY", "手选数据源不能为空，请选择具体数据", context.componentId,
						context.componentType, "dataSource.targetIds"));
		}
		if (hasTargets) {
			switch (context.componentType) {
				case PageDesignComponentTypes.GOODS_GROUP, PageDesignComponentTypes.GOODS_RANKING,
						PageDesignComponentTypes.GOODS_RECOMMEND ->
					context.goodsIds.addAll(targetIds.toJavaList(String.class));
				case PageDesignComponentTypes.LIMITED_ACTIVITY, PageDesignComponentTypes.SECKILL,
						PageDesignComponentTypes.DISCOUNT ->
					context.activityIds.addAll(targetIds.toJavaList(String.class));
				default -> {
				}
			}
		}
		if (!MANUAL_MODE.equals(mode)) {
			context.requestCount++;
		}
	}

	/**
	 * 自定义 HTML 组件的 XSS 阻断式校验。
	 * <p>
	 * 仅对 type=custom-html 生效；props.html 为空时放行（空组件不报错）。
	 * 命中危险特征即阻断发布，错误信息明确指出命中的标签/属性类型。
	 */
	private void validateCustomHtml(JSONObject props, String componentId, String componentType,
			String basePath, PageDesignValidationVO result) {
		if (!PageDesignComponentTypes.CUSTOM_HTML.equals(componentType)) {
			return;
		}
		String html = props.getString("html");
		if (!StringUtils.hasText(html)) {
			return;
		}
		// 原始形态与「实体解码后」的形态都要查：前者拦住字面量，
		// 后者拦住 &lt;script / &#106;avascript: / &lt;img onerror= 这类编码绕过。
		for (String candidate : safetyCandidates(html)) {
			String reason = findDangerousFeature(candidate);
			if (reason != null) {
				result.getErrors()
					.add(issue("CUSTOM_HTML_UNSAFE", "自定义HTML包含不安全标签/属性：" + reason, componentId,
							componentType, basePath + ".props.html"));
				return;
			}
		}
	}

	/**
	 * 待检形态：原文、解码后的文本，以及各自的「去空白压缩」版本。
	 * <p>
	 * 压缩版本用来覆盖 `java\nscript:`、`on\nerror=` 这类靠插入空白绕过子串匹配、
	 * 但浏览器仍会正常执行的写法。
	 */
	private List<String> safetyCandidates(String html) {
		String decoded = decodeHtmlEntities(html);
		List<String> candidates = new ArrayList<>(4);
		for (String value : List.of(html, decoded)) {
			String lower = value.toLowerCase(Locale.ROOT);
			candidates.add(lower);
			candidates.add(compactForSafetyCheck(lower));
		}
		return candidates;
	}

	/**
	 * 命中危险特征时返回其可读名称，未命中返回 {@code null}。
	 */
	private String findDangerousFeature(String candidate) {
		for (Map.Entry<String, String> marker : CUSTOM_HTML_DANGER_MARKERS.entrySet()) {
			if (candidate.contains(marker.getKey())) {
				return marker.getValue();
			}
		}
		if (EVENT_HANDLER_PATTERN.matcher(candidate).find()) {
			return "事件处理器属性(on*)";
		}
		return null;
	}

	private boolean isImageValue(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		String lowerValue = value.toLowerCase(Locale.ROOT);
		for (String extension : IMAGE_EXTENSIONS) {
			if (lowerValue.contains(extension)) {
				return true;
			}
		}
		String lowerKey = key.toLowerCase(Locale.ROOT);
		if (!lowerKey.contains("url") && !lowerKey.contains("img") && !lowerKey.contains("pic")
				&& !lowerKey.contains("image")) {
			return false;
		}
		for (String keyword : IMAGE_KEY_WORDS) {
			if (lowerKey.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private void appendBudgetWarnings(PageDesignValidationVO result) {
		PageDesignValidationVO.Budget budget = result.getPerformance();
		if (budget.getComponentCount() > MAX_COMPONENTS) {
			result.getWarnings()
				.add(issue("BUDGET_COMPONENTS", "页面组件数量超过" + MAX_COMPONENTS + "，可能影响编辑与渲染性能", null, null,
						null));
		}
		if (budget.getImageCount() > MAX_IMAGES) {
			result.getWarnings()
				.add(issue("BUDGET_IMAGES", "页面图片数量超过" + MAX_IMAGES + "，建议压缩或合并图片素材", null, null, null));
		}
		if (budget.getRequestCount() > MAX_REQUESTS) {
			result.getWarnings()
				.add(issue("BUDGET_REQUESTS", "预估首屏接口请求超过" + MAX_REQUESTS + "，建议合并数据源", null, null, null));
		}
		if (budget.getContentBytes() > MAX_CONTENT_BYTES) {
			result.getWarnings().add(issue("BUDGET_SIZE", "页面内容体积过大，建议精简组件配置", null, null, null));
		}
	}

	private PageDesignValidationVO.Issue issue(String code, String message, String componentId, String componentType,
			String field) {
		PageDesignValidationVO.Issue validationIssue = new PageDesignValidationVO.Issue();
		validationIssue.setCode(code);
		validationIssue.setMessage(message);
		validationIssue.setComponentId(componentId);
		validationIssue.setComponentType(componentType);
		validationIssue.setField(field);
		return validationIssue;
	}

	private record ComponentEntry(JSONObject component, String path) {
	}

	private static final class ValidationContext {

		private final String componentId;

		private final String componentType;

		private final List<String> goodsIds = new ArrayList<>();

		private final List<String> couponIds = new ArrayList<>();

		private final List<String> activityIds = new ArrayList<>();

		private final List<String> categoryIds = new ArrayList<>();

		private int imageCount;

		private int requestCount;

		private ValidationContext(String componentId, String componentType) {
			this.componentId = componentId;
			this.componentType = componentType;
		}

	}

}
