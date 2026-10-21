
package com.aryn.cloud.order.api.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author 雨滴kian
 * @description
 * @date 2025/1/29
 */

@Data
public class OrderStatisticsDTO {

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime startTime;

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime endTime;

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime compareStartTime;

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime compareEndTime;

	/**
	 * 是否返回消费、增长、RFM、Cohort 等用户诊断数据；分析页可不传以降低查询量。
	 */
	private Boolean includeUserAnalytics;

}
