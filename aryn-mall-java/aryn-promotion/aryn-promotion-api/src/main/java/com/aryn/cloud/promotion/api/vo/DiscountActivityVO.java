package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "折扣活动VO(管理端)")
public class DiscountActivityVO {

	@Schema(description = "主键")
	private String id;

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

	@Schema(description = "活动描述")
	private String description;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "关联商品数")
	private Long goodsCount;

	@Schema(description = "商品列表")
	private List<DiscountGoodsVO> goodsList;
}