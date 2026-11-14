package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 新人专享页三场景卡片。
 *
 * <p>个人场景承载 type=9 新人价商品；船供/企业场景承载 type=7 首单整船优惠，
 * 只返回活动与门槛档位，不伪造商品列表。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@Schema(description = "新人场景卡片")
public class AppNewbieSceneVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "场景：1个人 2船供 3企业")
	private String scene;

	@Schema(description = "场景标题")
	private String sceneTitle;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "活动类型：7整船优惠 9新人价")
	private String activityType;

	@Schema(description = "结束时间；长期有效时仍下发远期时间，由 longTerm 决定是否展示倒计时")
	private LocalDateTime endTime;

	@Schema(description = "是否长期有效（true 时 C 端不展示结束倒计时）")
	private Boolean longTerm;

	@Schema(description = "是否可享：true/false/null（未登录或缺主体）")
	private Boolean eligible;

	@Schema(description = "不可享原因")
	private String ineligibleReason;

	@Schema(description = "首单主体类型：1船 2采购主体")
	private String subjectType;

	@Schema(description = "单笔封顶金额")
	private BigDecimal maxDiscountAmount;

	@Schema(description = "整船优惠门槛档位")
	private List<TierVO> tiers;

	@Schema(description = "新人价商品（仅个人场景）")
	private List<AppNewbieFloorVO.NewbieGoodsVO> goodsList;

	@Data
	@Schema(description = "整船优惠门槛")
	public static class TierVO implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "门槛金额")
		private BigDecimal minAmount;

		@Schema(description = "优惠金额")
		private BigDecimal discountAmount;

	}

}
