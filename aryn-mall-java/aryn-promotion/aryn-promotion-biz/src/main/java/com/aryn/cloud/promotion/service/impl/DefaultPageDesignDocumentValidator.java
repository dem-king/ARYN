package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.promotion.api.constant.PageDesignComponentTypes;
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

	@Override
	public List<String> validate(String pageContent) {
		return validateStructured(pageContent).getErrors().stream().map(PageDesignValidationVO.Issue::getMessage)
			.toList();
	}

	@Override
	public PageDesignValidationVO validateStructured(String pageContent) {
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
				case PageDesignComponentTypes.GOODS_GROUP, PageDesignComponentTypes.GOODS_RANKING ->
					context.goodsIds.addAll(targetIds.toJavaList(String.class));
				case PageDesignComponentTypes.LIMITED_ACTIVITY ->
					context.activityIds.addAll(targetIds.toJavaList(String.class));
				default -> {
				}
			}
		}
		if (!MANUAL_MODE.equals(mode)) {
			context.requestCount++;
		}
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
