package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版权配置（渲染端下发值）。
 *
 * <p>名称与 Logo 已在服务端回落到租户品牌，前端拿到即可直接渲染，不必再请求
 * 门店信息接口拼品牌；两者都缺失时为空串，由前端内置默认兜底。</p>
 */
@Data
@Schema(description = "版权配置（下发）")
public class AppCopyrightConfigVO {

	@Schema(description = "端类型：app 用户端；merchant 商户端；platform 平台端")
	private String clientType;

	@Schema(description = "Logo（已回落租户品牌）")
	private String logoUrl;

	@Schema(description = "名称（已回落租户品牌）")
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