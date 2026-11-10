package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端新人专享楼层 VO。
 *
 * <p>价格策略：接口固定返回活动配置价与当前售价（不在本接口按人出价）；资格以三态
 * {@code eligible} 下发，供展示端决定「露出楼层/专区」还是「对老客隐藏」：
 * null=未登录/未知（按潜在新人展示），true=可享，false=已登录且确定不符合。
 * 真正拦截仍在下单链路，展示端的资格只影响可见性。
 *
 * @author aryn
 * @since 2026/10/13
 */
@Data
@Schema(description = "C端新人专享楼层VO")
public class AppNewbieFloorVO implements Serializable {

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

	@Schema(description = "活动结束时间；长期有效时仍下发远期时间，由 longTerm 决定是否展示倒计时")
	private LocalDateTime endTime;

	@Schema(description = "是否长期有效（true 时 C 端不展示结束倒计时）")
	private Boolean longTerm;

	@Schema(description = "当前用户是否可享：true 可享；false 已登录但确定不符合；null 未登录/未知")
	private Boolean eligible;

	@Schema(description = "不具备资格的原因：ALREADY_PAID/REGISTER_TIMEOUT/PARAM_INVALID/ERROR；仅 eligible=false 时有值")
	private String ineligibleReason;

	@Schema(description = "新人价商品列表")
	private List<NewbieGoodsVO> goodsList;

	@Data
	@Schema(description = "新人价商品项")
	public static class NewbieGoodsVO implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "商品名称")
		private String goodsName;

		@Schema(description = "商品子标题（与商品名相同或为空时展示端不渲染）")
		private String subTitle;

		@Schema(description = "商品图片")
		private String goodsImage;

		@Schema(description = "新人专享价")
		private BigDecimal newbiePrice;

		@Schema(description = "划线原价（当前售价）")
		private BigDecimal originalPrice;

	}

}
