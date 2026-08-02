package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "C端秒杀会场VO")
public class AppSeckillVO {

	@Schema(description = "场次ID")
	private String sessionId;

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

	@Schema(description = "倒计时(秒)")
	private Long countdown;

	@Schema(description = "商品列表")
	private List<AppSeckillGoodsVO> goodsList;
}