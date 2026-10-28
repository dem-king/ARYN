package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.constant.PageDesignComponentTypes;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPageDesignDocumentValidator implements PageDesignDocumentValidator {

	/**
	 * 活动表 Mapper：仅用于多 Tab {@code activity} 模式在装修保存/发布时校验
	 * 「指定活动存在且为会员价活动（type=10）」；不参与其它校验。
	 */
	private final PromotionActivityMapper promotionActivityMapper;

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

	/**
	 * 多 Tab 商品流的活动数据源模式：商品 = 会员价活动（type=10）里配置的 SKU 对应 SPU，
	 * 顺序 = 活动 rules.skus 配置顺序（前端传 activityId 固定活动，留空则跟随当前进行中活动）。
	 */
	private static final String ACTIVITY_MODE = "activity";

	/** 多 Tab 商品流的自动 Tab 支持的商品排序（F1：bargain=折扣力度优先）。 */
	private static final Set<String> GOODS_TAB_AUTOMATIC_SORTS = Set.of("sales", "create_time", "sales_price",
			"bargain");

	/** 折扣力度优先的折扣率阈值区间 (1.00, 9.99]，缺省 1.15（与 F1 接口约定一致）。 */
	private static final java.math.BigDecimal BARGAIN_RATE_MIN_EXCLUSIVE = new java.math.BigDecimal("1.00");

	private static final java.math.BigDecimal BARGAIN_RATE_MAX_INCLUSIVE = new java.math.BigDecimal("9.99");

	/** 会员权益条自定义文案长度上限（字）：会员/游客文案各自独立计算。 */
	private static final int MEMBER_BANNER_COPY_MAX_LENGTH = 30;

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
		if (PageDesignComponentTypes.GOODS_TABS.equals(componentType)) {
			validateGoodsTabs(props, context, result);
		}
		else {
			validateDataSource(props, context, result);
		}
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
	 * 多 Tab 商品流的数据源校验。
	 * <p>
	 * 数据源挂在每个 Tab 上，不能复用根节点 dataSource 的通用校验：
	 * 手选 Tab 必须选商品，自动 Tab 计入页面请求预算，手选商品进入引用清单。
	 */
	private void validateGoodsTabs(JSONObject props, ValidationContext context, PageDesignValidationVO result) {
		JSONArray tabs = props.getJSONArray("tabs");
		if (tabs == null || tabs.isEmpty()) {
			result.getErrors()
				.add(issue("DATASOURCE_EMPTY", "多Tab商品流至少需要配置一个Tab", context.componentId,
						context.componentType, "props.tabs"));
			return;
		}
		if (tabs.size() > 6) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "多Tab商品流最多配置 6 个 Tab", context.componentId,
						context.componentType, "props.tabs"));
		}
		Set<String> tabIds = new HashSet<>();
		for (int index = 0; index < tabs.size(); index++) {
			JSONObject tab = tabs.getJSONObject(index);
			if (tab == null) {
				continue;
			}
			String path = "props.tabs[" + index + "]";
			if (!StringUtils.hasText(tab.getString("title"))) {
				result.getErrors()
					.add(issue("DATASOURCE_INVALID", "Tab 标题不能为空", context.componentId, context.componentType,
							path + ".title"));
			}
			String tabId = tab.getString("id");
			if (StringUtils.hasText(tabId) && !tabIds.add(tabId)) {
				result.getErrors()
					.add(issue("DATASOURCE_INVALID", "Tab 标识重复：" + tabId, context.componentId,
							context.componentType, path + ".id"));
			}
			JSONObject dataSource = tab.getJSONObject("dataSource");
			if (dataSource == null) {
				result.getErrors()
					.add(issue("DATASOURCE_EMPTY", "Tab 数据源不能为空", context.componentId, context.componentType,
							path + ".dataSource"));
				continue;
			}
			String mode = dataSource.getString("mode");
			boolean manualMode = MANUAL_MODE.equals(mode);
			boolean activityMode = ACTIVITY_MODE.equals(mode);
			JSONArray targetIds = dataSource.getJSONArray("targetIds");
			boolean hasTargets = targetIds != null && !targetIds.isEmpty();
			if (manualMode && !hasTargets) {
				result.getErrors()
					.add(issue("DATASOURCE_EMPTY", "手选数据源不能为空，请选择具体数据", context.componentId,
							context.componentType, path + ".dataSource.targetIds"));
			}
			// activity 模式忽略 targetIds（切换数据源后装修器可能残留旧手选 ID，不校验也不进引用清单）
			if (hasTargets && !activityMode) {
				context.goodsIds.addAll(targetIds.toJavaList(String.class));
			}
			if (activityMode) {
				// 活动商品数据源 = 活动商品接口 + 按 SPU 取商品列表接口，共 2 次请求（不校验 sort/category/targetIds）
				context.requestCount += 2;
				validateActivityTabSource(dataSource, context, result, path);
			}
			else if (!manualMode) {
				context.requestCount++;
				// 自动 Tab：排序语义白名单 + 折扣力度区间（F1），脏配置会让 C 端落到默认排序而不报错
				validateAutomaticTabSort(dataSource, context, result, path);
			}
			// Tab 级会员权益条（F2）：只会透出文案，不拦截购买；但超长文案会破坏 C 端布局，发布前拦住
			validateMemberBanner(tab, context, result, path);
		}
	}

	/**
	 * 自动 Tab 的商品排序校验（与 C 端 retail-query / 商品列表接口同一白名单口径）：
	 * sales 销量 / create_time 新品 / sales_price 价格 / bargain 折扣力度优先。
	 * <p>
	 * sort 缺省放行（C 端按销量兜底，兼容存量文档）；sort=bargain 时 bargainRate 可空
	 * （缺省 1.15），给值必须落在 (1.00, 9.99]——区间由 C 端与 product 列表接口共同依赖。
	 */
	private void validateAutomaticTabSort(JSONObject dataSource, ValidationContext context,
			PageDesignValidationVO result, String path) {
		String sort = dataSource.getString("sort");
		if (StringUtils.hasText(sort) && !GOODS_TAB_AUTOMATIC_SORTS.contains(sort)) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "自动 Tab 排序仅支持销量/新品/价格/折扣力度，不支持：" + sort,
						context.componentId, context.componentType, path + ".dataSource.sort"));
			return;
		}
		if (!"bargain".equals(sort)) {
			return;
		}
		java.math.BigDecimal bargainRate;
		try {
			bargainRate = dataSource.getBigDecimal("bargainRate");
		}
		catch (Exception ex) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "折扣率阈值必须是数字", context.componentId, context.componentType,
						path + ".dataSource.bargainRate"));
			return;
		}
		if (bargainRate == null) {
			return;
		}
		if (bargainRate.compareTo(BARGAIN_RATE_MIN_EXCLUSIVE) <= 0
				|| bargainRate.compareTo(BARGAIN_RATE_MAX_INCLUSIVE) > 0) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "折扣率阈值必须大于 1.00 且不超过 9.99", context.componentId,
						context.componentType, path + ".dataSource.bargainRate"));
		}
	}

	/**
	 * 活动 Tab 数据源校验：会员专区商品自动取「会员价活动」里配置的商品（活动加减商品即同步）。
	 * <p>
	 * activityId 可空（运行时跟随当前进行中的会员价活动）；给值时在装修保存/发布阶段直查活动表，
	 * 校验「存在 + 会员价类型（type=10）」——避免运营选错活动（如秒杀/满减）导致专区永远空。
	 * 这里<b>不校验活动时间窗/状态</b>：允许提前装修、也允许活动结束后保留楼层配置（C 端价格接口
	 * 自然只对有效活动出价）。查询异常按校验失败处理（fail-closed，避免把无法确认的数据源发布出去）。
	 */
	private void validateActivityTabSource(JSONObject dataSource, ValidationContext context,
			PageDesignValidationVO result, String path) {
		String activityId = dataSource.getString("activityId");
		if (!StringUtils.hasText(activityId)) {
			// 留空 = 跟随当前进行中的会员价活动，无需查库
			return;
		}
		String fieldPath = path + ".dataSource.activityId";
		PromotionActivity activity;
		try {
			// 显式带租户条件（与 tenant 拦截器双保险）：避免跨租户按 ID 命中的极端情况
			String tenantId = ArynTenantContextHolder.getTenantId();
			activity = promotionActivityMapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getId, activityId.trim())
				.eq(StringUtils.hasText(tenantId), PromotionActivity::getTenantId, tenantId));
		}
		catch (Exception ex) {
			log.warn("装修活动数据源校验查询活动失败 activityId={}: {}", activityId, ex.getMessage());
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "会员价活动校验失败，请稍后重试", context.componentId,
						context.componentType, fieldPath));
			return;
		}
		if (activity == null) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "会员价活动不存在：" + activityId, context.componentId,
						context.componentType, fieldPath));
			return;
		}
		if (!PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "所选活动不是会员价活动，请选择会员价类型（type=10）",
						context.componentId, context.componentType, fieldPath));
		}
	}

	/**
	 * Tab 级会员权益条校验（F2）：{@code memberBanner:{enabled, memberCopy?, guestCopy?}}。
	 * <p>
	 * 开关关闭时不校验文案（配置文案可留作草稿）；开启时会员/游客文案各限 30 字，
	 * 文案留空走 C 端内置模板（不报错）。长度按 Unicode 码点计算，中文与 emoji 都算 1 字。
	 */
	private void validateMemberBanner(JSONObject tab, ValidationContext context, PageDesignValidationVO result,
			String path) {
		Object rawBanner = tab.get("memberBanner");
		if (rawBanner == null) {
			return;
		}
		if (!(rawBanner instanceof JSONObject banner)) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "会员权益条配置格式不正确", context.componentId,
						context.componentType, path + ".memberBanner"));
			return;
		}
		Boolean enabled;
		try {
			enabled = banner.getBoolean("enabled");
		}
		catch (Exception ex) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", "会员权益条开关必须是布尔值", context.componentId,
						context.componentType, path + ".memberBanner.enabled"));
			return;
		}
		if (!Boolean.TRUE.equals(enabled)) {
			return;
		}
		validateMemberBannerCopy(banner.getString("memberCopy"), "会员文案", context, result,
				path + ".memberBanner.memberCopy");
		validateMemberBannerCopy(banner.getString("guestCopy"), "游客文案", context, result,
				path + ".memberBanner.guestCopy");
	}

	private void validateMemberBannerCopy(String copy, String label, ValidationContext context,
			PageDesignValidationVO result, String fieldPath) {
		if (!StringUtils.hasText(copy)) {
			return;
		}
		int length = copy.codePointCount(0, copy.length());
		if (length > MEMBER_BANNER_COPY_MAX_LENGTH) {
			result.getErrors()
				.add(issue("DATASOURCE_INVALID", label + "不能超过 " + MEMBER_BANNER_COPY_MAX_LENGTH + " 字（当前 "
						+ length + " 字）", context.componentId, context.componentType, fieldPath));
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
