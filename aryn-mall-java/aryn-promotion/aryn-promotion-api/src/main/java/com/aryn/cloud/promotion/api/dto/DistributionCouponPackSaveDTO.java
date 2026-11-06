package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 券包新增/编辑请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "券包新增/编辑请求")
public class DistributionCouponPackSaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键（编辑时必填）")
	private String id;

	@Schema(description = "券包名称")
	@NotBlank(message = "请填写券包名称")
	private String packName;

	@Schema(description = "面额")
	@NotNull(message = "请填写面额")
	private BigDecimal faceAmount;

	@Schema(description = "使用门槛")
	private BigDecimal thresholdAmount;

	@Schema(description = "有效期（天）")
	private Integer validDays;

	@Schema(description = "消耗佣金")
	@NotNull(message = "请填写消耗佣金")
	private BigDecimal requiredCommission;

	@Schema(description = "库存（NULL=不限）")
	private Integer stock;

	@Schema(description = "状态：0上架 1下架")
	private String status;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "关联优惠券模板")
	private List<Item> items;

	/**
	 * 券包内优惠券模板明细
	 */
	@Data
	@Schema(description = "券包内优惠券模板明细")
	public static class Item implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "优惠券模板ID")
		private String couponInfoId;

		@Schema(description = "数量")
		private Integer quantity;

	}

}
