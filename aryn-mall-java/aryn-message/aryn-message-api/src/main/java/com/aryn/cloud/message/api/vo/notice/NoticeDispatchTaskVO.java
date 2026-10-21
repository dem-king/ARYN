package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 通知管理视图中的分发任务明细。 */
@Data
public class NoticeDispatchTaskVO implements Serializable {

	private String id;
	private String targetType;
	private String audienceType;
	private String status;
	private Long estimatedCount;
	private Long successCount;
	private Long failureCount;
	private Integer retryCount;
	private String errorSummary;
	private LocalDateTime lastHeartbeatTime;

}
