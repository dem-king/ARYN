package com.aryn.cloud.product.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品属性标签（商详首屏属性卡）。
 *
 * <p>由管理端按商品配置，存于 {@code goods_spu.attr_tags} JSON 数组；
 * 值、属性名是必填项，图标/色调/点击行为为可选项。</p>
 */
@Data
@Schema(description = "商品属性标签")
public class GoodsAttrTag implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "唯一键，前端列表 key")
	private String key;

	@Schema(description = "属性名，如 系列/储存条件")
	private String label;

	@Schema(description = "属性值，如 地标/冷藏")
	private String value;

	@Schema(description = "图标：预置 key 或图片 URL")
	private String icon;

	@Schema(description = "色调：green/blue/gray/orange")
	private String tone;

	@Schema(description = "点击类型：none/popup/page")
	private String linkType;

	@Schema(description = "弹窗标题，缺省取属性值")
	private String popupTitle;

	@Schema(description = "弹窗内容")
	private String popupContent;

	@Schema(description = "跳转页面路径，仅站内 /pages 或 /sub-pages")
	private String linkUrl;
}
