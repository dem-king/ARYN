package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "折扣活动DTO")
public class DiscountActivityDTO {

	@Schema(description = "主键(编辑时传)")
	private String id;

	@Schema(description = "活动名称")
	@NotBlank(message = "活动名称不能为空")
	private String activityName;

	@Schema(description = "开始时间")
	@NotNull(message = "开始时间不能为空")
	private LocalDateTime startTime;

	@Schema(description = "结束时间")
	@NotNull(message = "结束时间不能为空")
	private LocalDateTime endTime;

	@Schema(description = "折扣类型:1打折 2减价 3固定价")
	@NotNull(message = "折扣类型不能为空")
	private Integer discountType;

	@Schema(description = "折扣值")
	@NotNull(message = "折扣值不能为空")
	@Positive(message = "折扣值必须大于0")
	private BigDecimal discountValue;

	@Schema(description = "时间区间校验")
	public boolean isTimeRangeValid() {
		return startTime != null && endTime != null && startTime.isBefore(endTime);
	}

	@Schema(description = "适用范围:1全场 2指定商品")
	private Integer scope;

	@Schema(description = "活动描述")
	private String description;

	@Schema(description = "商品列表(scope=2时传)")
	@Valid
	private List<DiscountGoodsDTO> goodsList;
}