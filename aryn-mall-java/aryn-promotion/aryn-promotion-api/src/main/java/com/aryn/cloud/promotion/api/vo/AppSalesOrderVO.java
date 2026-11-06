package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 小程序「业绩明细」行
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "业绩明细")
public class AppSalesOrderVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "业绩单ID")
	private String id;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "支付时间")
	private LocalDateTime payTime;

	@Schema(description = "提成基数")
	private BigDecimal commissionBase;

	@Schema(description = "提成金额")
	private BigDecimal commissionAmount;

	@Schema(description = "状态：0待确认 1可发放 2已发放 3已全额退款")
	private String status;

}
