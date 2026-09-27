package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C端折扣活动会场项（按活动分组，含商品明细与实时折扣价）。
 *
 * <p>与 {@code DiscountActivityVO}（管理端）的区别：管理端只需要活动自身字段，
 * 且商品列表只在编辑时使用；本类面向 C 端会场，商品项已算好相对本活动的折扣价，
 * 并带上倒计时，供折扣页直接渲染。
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "C端折扣活动会场项")
public class AppDiscountActivityVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "开始时间")
	private LocalDateTime startTime;

	@Schema(description = "结束时间")
	private LocalDateTime endTime;

	@Schema(description = "折扣类型:1打折 2减价 3固定价")
	private Integer discountType;

	@Schema(description = "折扣值")
	private BigDecimal discountValue;

	@Schema(description = "适用范围:1全场 2指定商品")
	private Integer scope;

	@Schema(description = "状态:0未开始 1进行中 2已结束 3已暂停")
	private Integer status;

	@Schema(description = "倒计时(秒)，按进行中取距结束、未开始取距开始")
	private Long countdown;

	@Schema(description = "参与折扣的商品列表；全场(scope=1)活动为空，此时按折扣规则在商品详情页生效")
	private List<AppDiscountGoodsVO> goodsList;
}
