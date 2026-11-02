package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@Schema(description = "秒杀每日排期模板VO")
public class SeckillSessionTemplateVO {

	@Schema(description = "模板ID")
	private String id;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "场次名称")
	private String sessionName;

	@Schema(description = "每日开始时间")
	private LocalTime startTime;

	@Schema(description = "每日结束时间")
	private LocalTime endTime;

	@Schema(description = "生效星期(1-7逗号分隔)")
	private String weekdays;

	@Schema(description = "状态:0启用 1停用")
	private Integer status;

	@Schema(description = "商品列表")
	private List<SeckillGoodsVO> goodsList;
}
