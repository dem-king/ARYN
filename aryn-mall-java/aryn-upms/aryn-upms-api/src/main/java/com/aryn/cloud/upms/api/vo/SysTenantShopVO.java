package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "租户门店公开信息")
public class SysTenantShopVO {

	@Schema(description = "租户ID")
	private String id;

	@Schema(description = "门店名称")
	private String name;

	@Schema(description = "门店Logo")
	private String logoUrl;

	@Schema(description = "门店地址")
	private String address;

	@Schema(description = "官网地址")
	private String siteUrl;

	@Schema(description = "联系电话")
	private String phone;

	/**
	 * 业务模式：1 综合（个人购买 + 船供采购并存）；2 纯零售。
	 * C 端据此决定是否渲染船舶工作台等船供专属入口。
	 */
	@Schema(description = "业务模式：1综合 2纯零售")
	private String businessMode;

}
