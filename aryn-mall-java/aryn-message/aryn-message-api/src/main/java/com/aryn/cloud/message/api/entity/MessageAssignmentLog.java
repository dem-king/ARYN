package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客服分配审计日志。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_assignment_log")
public class MessageAssignmentLog extends AbstractMessageEntity {

	private String conversationId;
	private String actionType;
	private String fromStaffId;
	private String toStaffId;
	private String operatorType;
	private String operatorId;
	private String reason;
	private String detailPayload;

}
