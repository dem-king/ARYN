package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "秒杀场次VO")
public class SeckillSessionVO {

	@Schema(description = "场次ID")
	private String id;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "场次名称")
	private String sessionName;

	@Schema(description = "场次开始时间")
	private LocalDateTime startTime;

	@Schema(description = "场次结束时间")
	private LocalDateTime endTime;

	@Schema(description = "状态:0未开始 1进行中 2已结束")
	private Integer status;

	@Schema(description = "商品列表")
	private List<SeckillGoodsVO> goodsList;
}