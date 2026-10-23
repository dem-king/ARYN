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

	/**
	 * 本场「N 人在抢」的去重人数：场次内 SKU 当前仍在购物车里的人数。
	 * 只读统计（order 域远程查询），拿不到时为 null，C 端据此隐藏该行。
	 */
	@Schema(description = "本场在抢人数（购物车去重人数，可能为空）")
	private Integer cartUserCount;

	@Schema(description = "商品列表")
	private List<AppSeckillGoodsVO> goodsList;
}