package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端采购档期 VO（档期楼层/特供会场）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "采购档期VO")
public class AppCalendarVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "档期ID")
	private String id;

	@Schema(description = "档期名称")
	private String calendarName;

	@Schema(description = "锚点类型：1周度 2靠港 3自然月 4自定义")
	private String anchorType;

	@Schema(description = "档期开始")
	private LocalDateTime startTime;

	@Schema(description = "档期结束")
	private LocalDateTime endTime;

}
