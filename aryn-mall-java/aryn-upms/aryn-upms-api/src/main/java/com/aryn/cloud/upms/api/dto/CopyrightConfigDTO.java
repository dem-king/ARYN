package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版权配置保存入参。
 *
 * <p>只接受可编辑字段：主键、租户归属与审计列由服务端决定，避免请求体改挂记录归属。
 * 名称/Logo/版权信息留空即清除该项（回落租户品牌或前端内置文案），因此这里不能用
 * 「非空才更新」的实体直更策略。</p>
 */
@Data
@Schema(description = "版权配置保存入参")
public class CopyrightConfigDTO {

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