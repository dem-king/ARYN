package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版权配置（管理端原始值）。
 *
 * <p>与 {@link AppCopyrightConfigVO} 的区别：本 VO 不做租户品牌回落，配置页要如实
 * 展示「留空 = 跟随租户品牌」，回落后的值会让运营误以为已经填过。</p>
 */
@Data
@Schema(description = "版权配置")
public class CopyrightConfigVO {

	@Schema(description = "端类型：app 用户端；merchant 商户端；platform 平台端")
	private String clientType;

	@Schema(description = "Logo（留空跟随租户品牌）")
	private String logoUrl;

	@Schema(description = "名称（留空跟随租户品牌）")
	private String name;

	@Schema(description = "版权信息")
	private String copyrightText;

	@Schema(description = "版权链接")
	private String copyrightLink;

	@Schema(description = "ICP备案号")
	private String icp;

	@Schema(description = "ICP网站链接")
	private String icpLink;

	@Schema(description = "登录页标题（仅平台端）")
	private String loginTitle;

	@Schema(description = "登录页副标题（仅平台端）")
	private String loginSubtitle;

	@Schema(description = "登录页图标（仅平台端）")
	private String loginIcon;

}