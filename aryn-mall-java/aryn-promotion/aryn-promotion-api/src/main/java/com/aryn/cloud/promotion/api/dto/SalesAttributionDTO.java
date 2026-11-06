package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单归属销售解析结果（Dubbo 返回）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "订单归属销售解析结果")
public class SalesAttributionDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "归属销售ID")
	private String salesStaffId;

	@Schema(description = "归属销售姓名")
	private String salesStaffName;

}
