package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "秒杀开抢提醒配置")
public class SeckillReminderConfigVO {

	@Schema(description = "是否开放提醒")
	private Boolean enabled;

	@Schema(description = "微信订阅模板ID（小程序 requestSubscribeMessage 使用）")
	private String templateId;

	@Schema(description = "提前提醒分钟数")
	private Integer offsetMinutes;
}
