package com.aryn.cloud.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户统计请求参数
 */
@Data
@Schema(description = "用户统计请求参数")
public class UserStatisticsDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "开始时间")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime startTime;

	@Schema(description = "结束时间")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime endTime;

}
