package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;

import java.util.List;

/** 客服会话自动分配、领取、转交、关闭和重开服务。 */
public interface ConversationAssignmentService {

	String autoAssign(String tenantId, String conversationId);

	List<ConversationVO> waiting(String tenantId, String queueCode, int limit);

	void claim(String tenantId, String conversationId, String staffId, String reason);

	void transfer(String tenantId, String conversationId, String operatorId, String targetStaffId, String reason,
			boolean supervisor);

	void close(String tenantId, String conversationId, MessageIdentityType operatorType, String operatorId,
			String reason, boolean supervisor);

	String reopenCustomerConversation(String tenantId, String customerId, String queueCode);

}
