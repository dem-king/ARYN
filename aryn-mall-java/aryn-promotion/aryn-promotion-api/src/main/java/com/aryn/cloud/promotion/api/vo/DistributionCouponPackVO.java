package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 券包视图（含关联优惠券模板）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "券包视图")
@EqualsAndHashCode(callSuper = true)
public class DistributionCouponPackVO extends DistributionCouponPack {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "关联优惠券模板")
	private List<Item> couponInfos;

	/**
	 * 券模板信息
	 */
	@Data
	@Schema(description = "券模板信息")
	public static class Item implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "优惠券模板ID")
		private String couponInfoId;

		@Schema(description = "优惠券名称")
		private String name;

		@Schema(description = "面额")
		private java.math.BigDecimal faceAmount;

		@Schema(description = "数量")
		private Integer quantity;

	}

}
