package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商城默认主题（C 端启动换肤）。
 * <p>
 * 由 /app/pagedesign/mall-theme 免登下发；未设置默认主题时返回 null，
 * C 端回落到内置默认配色。页面级 themeSnapshot 存在时优先于本配置。
 *
 * @author 雨滴kian
 * @date 2026/10/02
 */
@Data
@Schema(description = "商城默认主题")
public class AppMallThemeVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主题 ID")
	private String themeId;

	@Schema(description = "主题名称")
	private String themeName;

	@Schema(description = "品牌主色")
	private String primaryColor;

	@Schema(description = "品牌辅色（由主色衍生的浅色）")
	private String secondaryColor;

	@Schema(description = "页面背景色")
	private String pageBackgroundColor;

	@Schema(description = "导航栏背景色")
	private String navigationColor;

	@Schema(description = "导航栏文字色")
	private String navigationTextColor;

	@Schema(description = "全局圆角（px）")
	private Integer radius;

	/**
	 * 自动动效总开关：'1' 开启（默认）、'0' 关闭。
	 * <p>
	 * C 端据此决定是否启动自动轮播/自动前进等持续动效。挂在商城默认主题上
	 * 是为了「同一趟请求、同一份缓存」——开关随主题在首帧就位，不会先动再停。
	 * 兜底的是小程序没有 prefers-reduced-motion 等价能力这一缺口。
	 */
	@Schema(description = "自动动效总开关：1.开启；0.关闭")
	private String autoMotionEnabled;

}
