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

}
