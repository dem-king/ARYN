package com.aryn.cloud.promotion.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.vo.PageDesignAssetVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 页面装修素材引用检查服务。
 * <p>
 * 从草稿内容中提取图片/视频引用，标记外链与非 https 引用；
 * 同一地址在多个组件复用时去重合并展示。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Service
@RequiredArgsConstructor
public class PageDesignAssetService {

	private static final int SCHEMA_VERSION_V3 = 3;

	private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg",
			".avif");

	private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".m3u8", ".webm", ".flv");

	private static final Set<String> MEDIA_KEY_WORDS = Set.of("img", "image", "pic", "icon", "logo", "avatar",
			"banner", "bg", "background", "poster", "cover", "photo", "thumb", "video", "url");

	private final PageDesignMapper pageDesignMapper;

	/**
	 * 检查页面草稿引用的素材。
	 */
	public PageDesignAssetVO check(String pageId) {
		PageDesign page = pageDesignMapper.selectById(pageId);
		if (page == null) {
			throw new ArynBusinessException("页面不存在或无权访问");
		}
		Map<String, PageDesignAssetVO.AssetRef> refs = new LinkedHashMap<>();
		JSONObject document = parseContent(page.getPageContent());
		if (document != null) {
			for (JSONObject component : collectComponents(document)) {
				walk(component.getJSONObject("props"), component.getString("id"), component.getString("type"), refs);
			}
		}
		PageDesignAssetVO result = new PageDesignAssetVO();
		result.setPageDesignId(pageId);
		result.setAssets(new ArrayList<>(refs.values()));
		for (PageDesignAssetVO.AssetRef ref : refs.values()) {
			if ("video".equals(ref.getMediaType())) {
				result.setVideoCount(result.getVideoCount() + 1);
			}
			else {
				result.setImageCount(result.getImageCount() + 1);
			}
			if (ref.isInsecure()) {
				result.setInsecureCount(result.getInsecureCount() + 1);
			}
		}
		return result;
	}

	private List<JSONObject> collectComponents(JSONObject document) {
		List<JSONObject> components = new ArrayList<>();
		JSONArray array;
		if (document.getIntValue("schemaVersion") == SCHEMA_VERSION_V3) {
			JSONArray sections = document.getJSONArray("sections");
			JSONArray flattened = new JSONArray();
			if (sections != null) {
				for (int index = 0; index < sections.size(); index++) {
					JSONObject section = sections.getJSONObject(index);
					if (section != null && section.getJSONArray("components") != null) {
						flattened.addAll(section.getJSONArray("components"));
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
			if (component != null) {
				components.add(component);
			}
		}
		return components;
	}

	private void walk(JSONObject current, String componentId, String componentType,
			Map<String, PageDesignAssetVO.AssetRef> refs) {
		if (current == null) {
			return;
		}
		for (String key : current.keySet()) {
			Object value = current.get(key);
			if (value instanceof JSONObject nested) {
				walk(nested, componentId, componentType, refs);
			}
			else if (value instanceof JSONArray array) {
				for (int index = 0; index < array.size(); index++) {
					Object item = array.get(index);
					if (item instanceof JSONObject nested) {
						walk(nested, componentId, componentType, refs);
					}
					else if (item instanceof String text && isMediaValue(key, text)) {
						collect(text, componentId, componentType, refs);
					}
				}
			}
			else if (value instanceof String text && isMediaValue(key, text)) {
				collect(text, componentId, componentType, refs);
			}
		}
	}

	private void collect(String url, String componentId, String componentType,
			Map<String, PageDesignAssetVO.AssetRef> refs) {
		if (refs.containsKey(url)) {
			return;
		}
		PageDesignAssetVO.AssetRef ref = new PageDesignAssetVO.AssetRef();
		ref.setComponentId(componentId);
		ref.setComponentType(componentType);
		ref.setUrl(url);
		ref.setMediaType(isVideoUrl(url) ? "video" : "image");
		boolean external = url.startsWith("http://") || url.startsWith("https://");
		ref.setExternal(external);
		ref.setInsecure(url.startsWith("http://"));
		refs.put(url, ref);
	}

	private boolean isMediaValue(String key, String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		String lowerValue = value.toLowerCase(Locale.ROOT);
		if (isVideoUrl(value)) {
			return true;
		}
		for (String extension : IMAGE_EXTENSIONS) {
			if (lowerValue.contains(extension)) {
				return true;
			}
		}
		String lowerKey = key.toLowerCase(Locale.ROOT);
		for (String keyword : MEDIA_KEY_WORDS) {
			if (lowerKey.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVideoUrl(String value) {
		String lowerValue = value.toLowerCase(Locale.ROOT);
		for (String extension : VIDEO_EXTENSIONS) {
			if (lowerValue.contains(extension)) {
				return true;
			}
		}
		return false;
	}

	private JSONObject parseContent(String pageContent) {
		if (!StringUtils.hasText(pageContent)) {
			return null;
		}
		try {
			return JSON.parseObject(pageContent);
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("页面装修内容无效，无法检查素材");
		}
	}

}
