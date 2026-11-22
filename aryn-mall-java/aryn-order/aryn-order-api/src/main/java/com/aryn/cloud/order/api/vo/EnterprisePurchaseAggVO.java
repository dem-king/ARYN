package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 企业采购额月度聚合行（企业等级月度对账 Job 用）。
 *
 * <p>口径：已支付且完成收货的企业场景订单（scene=2 按船舶、scene=3 按采购主体），
 * 按订单完成月份归属；已退款明细剔除、赠品不计，金额=实付商品金额（不含运费）。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Data
@Schema(description = "企业采购额月度聚合行")
public class EnterprisePurchaseAggVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "归属月份 yyyy-MM（按订单完成时间）")
	private String period;

	@Schema(description = "当月净采购额（元，不含运费，剔除已退款明细）")
	private BigDecimal amount;

}