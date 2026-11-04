package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员/出清专区让利报表（轻量版）。
 *
 * <p>口径：仅统计已支付订单；让利 = Σ（活动前单价 − 成交单价）× 数量 + 会员折扣金额，
 * 不含优惠券与积分；GMV 为商品实付（payment_price − freight_price）。
 * 数据来自下单时写入的 {@code order_item.zone_source / activity_base_price} 快照。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "专区让利报表")
public class ZoneReportVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private LocalDateTime startTime;

	private LocalDateTime endTime;

	@Schema(description = "专区订单数（去重）")
	private Long orderCount;

	@Schema(description = "专区商品件数")
	private Long itemCount;

	@Schema(description = "商品实付金额（不含运费）")
	private BigDecimal gmv;

	@Schema(description = "专区让利金额")
	private BigDecimal discount;

	private List<Item> items;

	@Data
	@Schema(description = "按专区拆分的让利")
	public static class Item implements Serializable {

		private static final long serialVersionUID = 1L;

		/** member / clearance */
		private String zoneSource;

		private Long orderCount;

		private Long itemCount;

		private BigDecimal gmv;

		private BigDecimal discount;

	}

}
