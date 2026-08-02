package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "秒杀活动DTO")
public class SeckillActivityDTO {

	@Schema(description = "主键(编辑时传)")
	private String id;

	@Schema(description = "活动名称")
	@NotBlank(message = "活动名称不能为空")
	private String activityName;

	@Schema(description = "活动开始时间")
	@NotNull(message = "活动开始时间不能为空")
	private LocalDateTime startTime;

	@Schema(description = "活动结束时间")
	@NotNull(message = "活动结束时间不能为空")
	private LocalDateTime endTime;

	@Schema(description = "活动描述")
	private String description;

	@Schema(description = "场次列表")
	@Valid
	private List<SeckillSessionDTO> sessions;

	@Schema(description = "时间区间校验")
	public boolean isTimeRangeValid() {
		return startTime != null && endTime != null && startTime.isBefore(endTime);
	}
}