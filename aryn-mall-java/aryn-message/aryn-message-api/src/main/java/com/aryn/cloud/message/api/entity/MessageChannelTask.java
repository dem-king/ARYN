package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 站内通知的外部消息通道发送任务。 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("message_channel_task")
public class MessageChannelTask extends AbstractMessageEntity {

	private String messageId;
	private String recipientType;
	private String recipientId;
	private String channel;
	private String templateCode;
	private String templateParams;
	private String sourceType;
	private String sourceKey;
	private String status;
	private Integer retryCount;
	private LocalDateTime nextRetryTime;
	private LocalDateTime lastAttemptTime;
	private String errorSummary;

}
