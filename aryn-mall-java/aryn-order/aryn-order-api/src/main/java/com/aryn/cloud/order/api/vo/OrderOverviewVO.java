package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单概览统计 VO
 */
@Data
@Schema(description = "订单概览统计数据")
public class OrderOverviewVO {

	@Schema(description = "售后申请数量")
	private Long afterSalesCount;

	@Schema(description = "退款完成数量")
	private Long refundCompletedCount;

	@Schema(description = "超时24小时未发货数量")
	private Long unshippedTimeoutCount;

	@Schema(description = "负面评价数量 (1, 2星)")
	private Long negativeAppraisalCount;

}
