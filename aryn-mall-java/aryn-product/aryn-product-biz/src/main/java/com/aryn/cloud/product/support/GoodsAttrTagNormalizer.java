package com.aryn.cloud.product.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsAttrTag;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 商品属性标签的边界归一化与校验。
 *
 * <p>属性标签由运营在后台自由配置，属于系统边界输入：值/属性名必填，
 * 颜色、点击类型收敛到固定枚举；弹窗必须有内容，页面跳转只允许站内路径，
 * 避免把任意 URL 或 CSS class 带进 C 端。</p>
 */
public final class GoodsAttrTagNormalizer {

	public static final int MAX_TAGS = 6;

	private static final int MAX_VALUE_LENGTH = 24;
	private static final int MAX_LABEL_LENGTH = 12;
	private static final int MAX_POPUP_TITLE_LENGTH = 30;
	private static final int MAX_POPUP_CONTENT_LENGTH = 500;
	private static final int MAX_LINK_URL_LENGTH = 500;
	private static final int MAX_ICON_URL_LENGTH = 255;

	private static final Set<String> TONES = Set.of("green", "blue", "gray", "orange");
	private static final Set<String> LINK_TYPES = Set.of("none", "popup", "page");
	private static final Set<String> ICONS = Set.of(
			"location", "snowflake", "trophy", "package",
			"security", "time", "shopping-bag", "star-filled");

	private GoodsAttrTagNormalizer() {
	}

	/**
	 * null 原样返回：管理端局部更新（上下架等）不携带该字段时，
	 * MyBatis-Plus 会忽略 null，保留库里的既有配置；空数组则代表清空。
	 */
	public static List<GoodsAttrTag> normalize(List<GoodsAttrTag> tags) {
		if (tags == null) {
			return null;
		}
		if (tags.size() > MAX_TAGS) {
			throw new ArynBusinessException("属性标签最多配置 " + MAX_TAGS + " 个");
		}
		List<GoodsAttrTag> normalized = new ArrayList<>();
		for (GoodsAttrTag source : tags) {
			if (source == null) {
				continue;
			}
			String value = trim(source.getValue());
			String label = trim(source.getLabel());
			// 后台编辑器的空占位行允许存在，提交时直接丢掉
			if (!StringUtils.hasText(value) && !StringUtils.hasText(label)) {
				continue;
			}
			if (!StringUtils.hasText(value) || !StringUtils.hasText(label)) {
				throw new ArynBusinessException("属性标签的值和属性名必须同时填写");
			}
			requireLength(value, MAX_VALUE_LENGTH, "属性标签值");
			requireLength(label, MAX_LABEL_LENGTH, "属性标签名");

			GoodsAttrTag target = new GoodsAttrTag();
			target.setKey("attr-" + (normalized.size() + 1));
			target.setValue(value);
			target.setLabel(label);
			target.setTone(normalizeTone(source.getTone()));
			target.setIcon(normalizeIcon(source.getIcon()));

			String linkType = normalizeLinkType(source.getLinkType());
			target.setLinkType(linkType);
			if ("popup".equals(linkType)) {
				normalizePopup(target, source);
			}
			else if ("page".equals(linkType)) {
				normalizePage(target, source);
			}
			normalized.add(target);
		}
		return normalized;
	}

	private static String normalizeTone(String tone) {
		String value = trim(tone);
		return TONES.contains(value) ? value : "gray";
	}

	private static String normalizeLinkType(String linkType) {
		String value = trim(linkType);
		return LINK_TYPES.contains(value) ? value : "none";
	}

	/** 只接受预置图标 key 或 http(s) 图片地址；未知值静默降级为无图标 */
	private static String normalizeIcon(String icon) {
		String value = trim(icon);
		if (!StringUtils.hasText(value) || "none".equals(value)) {
			return null;
		}
		if (value.startsWith("http://") || value.startsWith("https://")) {
			return value.length() <= MAX_ICON_URL_LENGTH ? value : null;
		}
		return ICONS.contains(value) ? value : null;
	}

	private static void normalizePopup(GoodsAttrTag target, GoodsAttrTag source) {
		String content = trim(source.getPopupContent());
		if (!StringUtils.hasText(content)) {
			throw new ArynBusinessException("属性标签「" + target.getValue() + "」的弹窗内容不能为空");
		}
		requireLength(content, MAX_POPUP_CONTENT_LENGTH, "属性标签弹窗内容");
		String title = trim(source.getPopupTitle());
		if (StringUtils.hasText(title)) {
			requireLength(title, MAX_POPUP_TITLE_LENGTH, "属性标签弹窗标题");
		}
		else {
			title = target.getValue();
		}
		target.setPopupTitle(title);
		target.setPopupContent(content);
	}

	private static void normalizePage(GoodsAttrTag target, GoodsAttrTag source) {
		String url = trim(source.getLinkUrl());
		if (!StringUtils.hasText(url)
				|| !(url.startsWith("/pages/") || url.startsWith("/sub-pages/"))) {
			throw new ArynBusinessException(
					"属性标签「" + target.getValue() + "」的跳转路径必须以 /pages/ 或 /sub-pages/ 开头");
		}
		requireLength(url, MAX_LINK_URL_LENGTH, "属性标签跳转路径");
		target.setLinkUrl(url);
	}

	private static void requireLength(String value, int max, String field) {
		if (value.length() > max) {
			throw new ArynBusinessException(field + "不能超过 " + max + " 个字符");
		}
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}
}
