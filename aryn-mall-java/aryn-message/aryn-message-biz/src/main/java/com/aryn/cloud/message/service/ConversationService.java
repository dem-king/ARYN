package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAssignmentLogVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationAttentionVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;

import java.util.List;

/** 会话创建、查询和已读服务。 */
public interface ConversationService {

	ConversationVO getOrCreateCustomerService(String tenantId, String customerId, String customerName,
			String customerAvatar, String queueCode, String contextPayload);

	ConversationVO initiateCustomerService(String tenantId, String staffId, String customerId, String queueCode,
			String contextPayload);

	ConversationVO get(String tenantId, MessageIdentityType identityType, String identityId, String conversationId);

	ConversationInboxPageVO inbox(String tenantId, MessageIdentityType identityType, String identityId,
			ConversationInboxQuery query);

	/**
	 * 主管视图：本租户全部会话（含已关闭），不要求调用者是会话参与者。
	 *
	 * <p>排序、status 过滤与不透明游标语义与 {@link #inbox} 完全一致，复用
	 * {@code ConversationInboxPageVO}；权限由 Controller 的
	 * {@code message:service:supervisor} 注解保证。</p>
	 */
	ConversationInboxPageVO adminInbox(String tenantId, ConversationInboxQuery query);

	/**
	 * 工作人员待办提醒：本人未读会话与共享池待领取会话。
	 *
	 * <p>共享池只在调用方是「启用且 ONLINE」的客服坐席时才计入（接待状态只约束承接新工作），
	 * 避免普通管理员或离线坐席看到自己当前无权领取的会话。</p>
	 */
	ConversationAttentionVO attention(String tenantId, MessageIdentityType identityType, String identityId,
			String queueCode, int limit);

	/** 主管视图的会话分配审计日志（按时间升序）。 */
	List<ConversationAssignmentLogVO> assignmentLogs(String tenantId, String conversationId);

	void markRead(String tenantId, MessageIdentityType identityType, String identityId, String conversationId,
			long lastReadSeq);

}
