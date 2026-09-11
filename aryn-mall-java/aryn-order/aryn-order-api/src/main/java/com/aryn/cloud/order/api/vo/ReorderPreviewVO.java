package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 再购预览 VO。
 *
 * <p>再来一单不直接下单：必须重新确认船舶、靠港计划、库存与价格；
 * 本 VO 返回原明细与当前可购状态，由用户确认后走正常结算。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "再购预览VO")
public class ReorderPreviewVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "原订单ID")
	private String orderId;

	@Schema(description = "原订单号")
	private String orderNo;

	@Schema(description = "原购买场景")
	private String purchaseScene;

	@Schema(description = "再购明细（含当前可购校验）")
	private List<ReorderItem> items;

	@Data
	@Schema(description = "再购明细项")
	public static class ReorderItem implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "商品名称快照")
		private String spuName;

		@Schema(description = "规格快照")
		private String specsInfo;

		@Schema(description = "图片快照")
		private String picUrl;

		@Schema(description = "原购买数量")
		private Integer originalQuantity;

		@Schema(description = "当前售价")
		private BigDecimal currentPrice;

		@Schema(description = "当前库存")
		private Integer currentStock;

		@Schema(description = "价格是否变化")
		private Boolean priceChanged;

		@Schema(description = "是否可购买（下架/无库存/找不到 SKU 均为否）")
		private Boolean purchasable;

		@Schema(description = "不可购原因")
		private String reason;

	}

}
