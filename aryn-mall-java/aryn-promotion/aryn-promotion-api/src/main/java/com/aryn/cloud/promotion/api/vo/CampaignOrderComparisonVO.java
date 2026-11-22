package com.aryn.cloud.promotion.api.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 商品券后应付，不含地址运费、积分及储值扣款。 */
@Data
public class CampaignOrderComparisonVO implements Serializable {
	private Quote baseline;
	private Quote proposed;

	@Data
	public static class Quote implements Serializable {
		private BigDecimal goodsPayable;
		private BigDecimal couponDiscount;
		private BigDecimal memberDiscount;
		private BigDecimal wholeDiscount;
		private String couponReason;
		private List<Line> lines;
	}

	@Data
	public static class Line implements Serializable {
		private String skuId;
		private Integer quantity;
		private BigDecimal unitPrice;
		private BigDecimal goodsPayable;
		private BigDecimal couponDiscount;
		private BigDecimal memberDiscount;
		private BigDecimal wholeDiscount;
	}
}
