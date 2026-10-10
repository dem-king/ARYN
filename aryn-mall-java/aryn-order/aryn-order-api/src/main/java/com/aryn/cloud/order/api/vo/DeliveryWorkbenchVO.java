package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 司机工作台首页数据：一次请求拿齐统计与全部在途趟次。
 *
 * <p>原先工作台为了拼出「待处理/今日已完成」要连发 5 个分页查询（每状态一次 total），
 * 汇总口径还散在前端；出车单又只取最新一趟，多趟在途时司机看不到其余订单。
 * 这里把统计与趟次合并成一个响应。
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "司机工作台首页数据")
public class DeliveryWorkbenchVO {

	@Schema(description = "待处理单数（待取货+配货中+待送达）")
	private Long pendingTaskCount;

	@Schema(description = "今日已完成单数（今天送达或签收）")
	private Long todayDoneCount;

	@Schema(description = "在途出车单（按创建时间升序，含每趟的订单摘要）")
	private List<DeliveryTripBriefVO> trips;

}
