package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 会话参与者。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_participant")
public class MessageParticipant extends AbstractMessageEntity {

	private String conversationId;
	private String participantType;
	private String participantId;
	private String participantName;
	private String participantAvatar;
	private Long lastReadSeq;
	private LocalDateTime joinedTime;
	private LocalDateTime exitedTime;
	private String participantStatus;
	private String notificationEnabled;

}
