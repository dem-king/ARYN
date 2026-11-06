package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 待转交认领视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "待转交认领视图")
@EqualsAndHashCode(callSuper = true)
public class SalesCustomerClaimVO extends SalesCustomerClaim {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "客户昵称")
	private String nickname;

	@Schema(description = "销售姓名")
	private String staffName;

}
