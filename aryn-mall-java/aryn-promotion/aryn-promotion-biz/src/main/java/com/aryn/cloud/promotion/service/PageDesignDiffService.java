package com.aryn.cloud.promotion.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignDiffVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 页面装修版本差异服务（组件级结构化 diff）。
 * <p>
 * 兼容 schema v2/v3：v3 文档先按 sections 拍平为组件集合再比对。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Service
@RequiredArgsConstructor
public class PageDesignDiffService {

	private static final int SCHEMA_VERSION_V3 = 3;

	private final PageDesignMapper pageDesignMapper;

	private final PageDesignVersionMapper versionMapper;

	public PageDesignDiffVO diff(String pageId, String fromVersionId, String toVersionId) {
		requirePage(pageId);
		PageDesignVersion from = requireVersion(pageId, fromVersionId, "起始");
		PageDesignVersion to = requireVersion(pageId, toVersionId, "目标");

		Map<String, JSONObject> fromComponents = componentMap(parseContent(from.getPageContent()));
		Map<String, JSONObject> toComponents = componentMap(parseContent(to.getPageContent()));

		PageDesignDiffVO result = new PageDesignDiffVO();
		result.setPageDesignId(pageId);
		result.setFromVersionId(from.getId());
		result.setFromVersionNo(from.getVersionNo());
		result.setToVersionId(to.getId());
		result.setToVersionNo(to.getVersionNo());
		result.setFromSchemaVersion(from.getSchemaVersion());
		result.setToSchemaVersion(to.getSchemaVersion());

		Set<String> allIds = new LinkedHashSet<>();
		allIds.addAll(fromComponents.keySet());
		allIds.addAll(toComponents.keySet());
		for (String componentId : allIds) {
			JSONObject fromComponent = fromComponents.get(componentId);
			JSONObject toComponent = toComponents.get(componentId);
			if (fromComponent == null) {
				result.getAdded().add(componentChange(toComponent));
			}
			else if (toComponent == null) {
				result.getRemoved().add(componentChange(fromComponent));
			}
			else if (!Objects.equals(fromComponent, toComponent)) {
				result.getChanged().add(componentUpdate(componentId, fromComponent, toComponent));
			}
		}
		result.setPageChanged(pageDiff(parseContent(from.getPageContent()), parseContent(to.getPageContent())));
		return result;
	}

	private PageDesign requirePage(String pageId) {
		PageDesign page = pageDesignMapper.selectById(pageId);
		if (page == null) {
			throw new ArynBusinessException("页面不存在或无权访问");
		}
		return page;
	}

	private PageDesignVersion requireVersion(String pageId, String versionId, String label) {
		PageDesignVersion version = versionMapper.selectById(versionId);
		if (version == null || !pageId.equals(version.getPageDesignId())) {
			throw new ArynBusinessException(label + "版本不存在或无权访问");
		}
		return version;
	}

	private JSONObject parseContent(String pageContent) {
		if (!StringUtils.hasText(pageContent)) {
			return new JSONObject();
		}
		try {
			JSONObject document = JSON.parseObject(pageContent);
			return document == null ? new JSONObject() : document;
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("页面装修内容无效，无法比对");
		}
	}

	private Map<String, JSONObject> componentMap(JSONObject document) {
		Map<String, JSONObject> components = new LinkedHashMap<>();
		JSONArray array;
		if (document.getIntValue("schemaVersion") == SCHEMA_VERSION_V3) {
			JSONArray sections = document.getJSONArray("sections");
			JSONArray flattened = new JSONArray();
			if (sections != null) {
				for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
					JSONObject section = sections.getJSONObject(sectionIndex);
					if (section == null) {
						continue;
					}
					JSONArray sectionComponents = section.getJSONArray("components");
					if (sectionComponents != null) {
						flattened.addAll(sectionComponents);
					}
				}
			}
			array = flattened;
		}
		else {
			array = document.getJSONArray("components");
		}
		if (array == null) {
			return components;
		}
		for (int index = 0; index < array.size(); index++) {
			JSONObject component = array.getJSONObject(index);
			String componentId = component == null ? null : component.getString("id");
			if (StringUtils.hasText(componentId)) {
				components.putIfAbsent(componentId, component);
			}
		}
		return components;
	}

	private PageDesignDiffVO.ComponentChange componentChange(JSONObject component) {
		PageDesignDiffVO.ComponentChange change = new PageDesignDiffVO.ComponentChange();
		change.setComponentId(component.getString("id"));
		change.setComponentType(component.getString("type"));
		return change;
	}

	private PageDesignDiffVO.ComponentUpdate componentUpdate(String componentId, JSONObject fromComponent,
			JSONObject toComponent) {
		PageDesignDiffVO.ComponentUpdate update = new PageDesignDiffVO.ComponentUpdate();
		update.setComponentId(componentId);
		update.setComponentType(toComponent.getString("type"));
		if (!Objects.equals(fromComponent.getString("type"), toComponent.getString("type"))) {
			update.getChanges().add(fieldChange("type", fromComponent.getString("type"), toComponent.getString("type")));
		}
		Object fromVersion = fromComponent.get("version");
		Object toVersion = toComponent.get("version");
		if (!Objects.equals(fromVersion, toVersion)) {
			update.getChanges().add(fieldChange("version", fromVersion, toVersion));
		}
		JSONObject fromProps = fromComponent.getJSONObject("props");
		JSONObject toProps = toComponent.getJSONObject("props");
		Set<String> propKeys = new LinkedHashSet<>();
		if (fromProps != null) {
			propKeys.addAll(fromProps.keySet());
		}
		if (toProps != null) {
			propKeys.addAll(toProps.keySet());
		}
		for (String propKey : propKeys) {
			Object fromValue = fromProps == null ? null : fromProps.get(propKey);
			Object toValue = toProps == null ? null : toProps.get(propKey);
			if (!Objects.equals(fromValue, toValue)) {
				update.getChanges().add(fieldChange("props." + propKey, fromValue, toValue));
			}
		}
		return update;
	}

	private List<PageDesignDiffVO.FieldChange> pageDiff(JSONObject fromDocument, JSONObject toDocument) {
		JSONObject fromPage = fromDocument.getJSONObject("page");
		JSONObject toPage = toDocument.getJSONObject("page");
		if (fromPage == null && toPage == null) {
			return new ArrayList<>();
		}
		Set<String> keys = new LinkedHashSet<>();
		if (fromPage != null) {
			keys.addAll(fromPage.keySet());
		}
		if (toPage != null) {
			keys.addAll(toPage.keySet());
		}
		List<PageDesignDiffVO.FieldChange> changes = new ArrayList<>();
		for (String key : keys) {
			Object fromValue = fromPage == null ? null : fromPage.get(key);
			Object toValue = toPage == null ? null : toPage.get(key);
			if (!Objects.equals(fromValue, toValue)) {
				changes.add(fieldChange("page." + key, fromValue, toValue));
			}
		}
		return changes;
	}

	private PageDesignDiffVO.FieldChange fieldChange(String field, Object from, Object to) {
		PageDesignDiffVO.FieldChange change = new PageDesignDiffVO.FieldChange();
		change.setField(field);
		change.setFrom(from);
		change.setTo(to);
		return change;
	}

}
