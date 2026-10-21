package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 原始明细与用户日汇总的对账结果。
 */
@Data
@Schema(description = "日汇总对账结果")
public class DailyReconcileVO {

	@Schema(description = "开始日期")
	private String startDate;

	@Schema(description = "结束日期")
	private String endDate;

	@Schema(description = "明细分 GMV")
	private BigDecimal rawGmv;

	@Schema(description = "明细支付订单数")
	private Integer rawPaidOrders;

	@Schema(description = "明细支付买家数")
	private Integer rawPaidBuyers;

	@Schema(description = "汇总 GMV")
	private BigDecimal aggregateGmv;

	@Schema(description = "汇总支付订单数")
	private Integer aggregatePaidOrders;

	@Schema(description = "汇总支付买家数")
	private Integer aggregatePaidBuyers;

	@Schema(description = "GMV 差异 (明细 - 汇总)")
	private BigDecimal gmvDiff;

	@Schema(description = "订单数差异 (明细 - 汇总)")
	private Integer orderDiff;

	@Schema(description = "买家数差异 (明细 - 汇总)")
	private Integer buyerDiff;

}
