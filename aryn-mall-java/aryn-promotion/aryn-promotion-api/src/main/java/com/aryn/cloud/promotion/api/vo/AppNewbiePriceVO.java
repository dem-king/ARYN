package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端指定 SKU 的新人价查询结果。
 *
 * <p>与楼层接口（{@link AppNewbieFloorVO}）的分工：楼层固定返回活动配置价、不做按人判定
 * （营销面，免登）；本接口供商详 / 购物车等「用户已看到具体商品」的页面使用，
 * **可选登录**——携带登录态时额外返回当前用户的新人资格，让页面在用户确定不符合
 * 资格时直接按原价展示并给出说明，避免「页面标新人价、结算按原价」的价差观感。
 *
 * <p>商品明细只包含「请求的 SKU ∩ 活动配置的 SKU」，未命中的请求 SKU 不下发。
 *
 * @author aryn
 * @since 2026/10/16
 */
@Data
@Schema(description = "C端指定SKU的新人价（可选登录，返回当前用户资格）")
public class AppNewbiePriceVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "注册时效（天），null/空表示不限")
	private Integer registerWithinDays;

	@Schema(description = "每人每 SKU 限购件数（固定 1）")
	private Integer limitPerUser;

	@Schema(description = "活动结束时间")
	private LocalDateTime endTime;

	@Schema(description = "当前用户是否具备新人资格；null=未登录/未知（展示端按可享新人价处理）")
	private Boolean eligible;

	@Schema(description = "不满足资格的原因：ALREADY_PAID/REGISTER_TIMEOUT/PARAM_INVALID/ERROR")
	private String ineligibleReason;

	@Schema(description = "命中的新人价商品（仅包含请求且被活动覆盖的 SKU）")
	private List<NewbieSkuPriceVO> items;

	@Data
	@Schema(description = "新人价商品项")
	public static class NewbieSkuPriceVO implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "新人专享价")
		private BigDecimal newbiePrice;

		@Schema(description = "划线原价（当前售价）")
		private BigDecimal originalPrice;

	}

}
