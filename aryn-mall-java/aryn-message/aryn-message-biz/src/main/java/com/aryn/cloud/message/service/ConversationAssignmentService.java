package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;

import java.util.List;

/** 客服会话自动分配、领取、转交、关闭和重开服务。 */
public interface ConversationAssignmentService {

	String autoAssign(String tenantId, String conversationId);

	/**
	 * 共享池待领取列表。
	 *
	 * <p>要求调用者是启用中的客服坐席（{@code message_agent.enabled='1'}）；
	 * 是否 ONLINE 由前端提示「先上线」，不阻断查看共享池。
	 */
	List<ConversationVO> waiting(String tenantId, String staffId, String queueCode, int limit);

	/**
	 * 手动领取会话。
	 *
	 * <p>坐席必须启用且 {@code presence = ONLINE}，否则业务错误
	 * 「当前离线或未开启接待，无法领取会话」（接待状态只约束承接新工作）。
	 */
	void claim(String tenantId, String conversationId, String staffId, String reason);

	/**
	 * 转交会话。
	 *
	 * <p>非主管：目标客服必须启用且 ONLINE；主管：允许离线目标，但审计动作记为
	 * {@code FORCE_ASSIGN}。成功后写入 SYSTEM 消息（code=CONVERSATION_TRANSFERRED）通知双方。
	 */
	void transfer(String tenantId, String conversationId, String operatorId, String targetStaffId, String reason,
			boolean supervisor);

	void close(String tenantId, String conversationId, MessageIdentityType operatorType, String operatorId,
			String reason, boolean supervisor);

	String reopenCustomerConversation(String tenantId, String customerId, String queueCode);

	/**
	 * 回收共享池待领取会话：按排队顺序逐个尝试自动分配，单条失败不影响后续。
	 *
	 * <p>坐席上线事件与维护任务都会调用；返回成功分配出去的会话数。
	 */
	int reassignWaitingPool(String tenantId, int limit);

}
