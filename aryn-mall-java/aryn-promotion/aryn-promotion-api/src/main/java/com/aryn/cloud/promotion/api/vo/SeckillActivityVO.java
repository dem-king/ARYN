package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "秒杀活动VO(管理端)")
public class SeckillActivityVO {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "活动开始时间")
	private LocalDateTime startTime;

	@Schema(description = "活动结束时间")
	private LocalDateTime endTime;

	@Schema(description = "状态:0未开始 1进行中 2已结束 3已暂停")
	private Integer status;

	@Schema(description = "活动描述")
	private String description;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "场次列表")
	private List<SeckillSessionVO> sessions;
}