package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 通知分发任务。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_dispatch_task")
public class MessageDispatchTask extends AbstractMessageEntity {

	private String messageId;
	private String targetType;
	private String audienceType;
	private String audienceCondition;
	private String cursorValue;
	private String status;
	private Long estimatedCount;
	private Long successCount;
	private Long failureCount;
	private Integer retryCount;
	private LocalDateTime lastHeartbeatTime;
	private String errorSummary;

}
