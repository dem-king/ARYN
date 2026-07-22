package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 消息会话。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_conversation")
public class MessageConversation extends AbstractMessageEntity {

	private String conversationType;
	private String queueCode;
	private String customerId;
	private String assignedStaffId;
	private String staffPairKey;
	private String status;
	private Long lastSeq;
	private String lastMessageId;
	private String lastMessageSummary;
	private LocalDateTime lastMessageTime;
	private LocalDateTime closedTime;
	private LocalDateTime reopenDeadline;
	private String closeReason;
	private String contextPayload;

	@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
	private String customerActiveKey;

	@TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
	private String staffActiveKey;

}
