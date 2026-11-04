package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;

import java.util.Collection;
import java.util.List;

/** 客服坐席配置与实时状态服务。 */
public interface AgentService {

	AgentVO saveConfig(String tenantId, String operatorId, AgentConfigRequest request);

	AgentVO get(String tenantId, String staffId);

	AgentVO findConfig(String tenantId, String staffId);

	StaffMessageAudiencePageVO listCandidates(String tenantId);

	AgentVO setPresence(String tenantId, String staffId, AgentPresenceStatus status);

	void heartbeat(String tenantId, String staffId);

	AgentPresenceStatus getPresence(String tenantId, String staffId);

	boolean isAutoAssignable(String tenantId, String staffId);

	/** 是否配置了启用中的客服坐席（message_agent.enabled='1'），不要求在线。 */
	boolean isEnabledAgent(String tenantId, String staffId);

	/**
	 * 是否可以承接新工作：坐席启用且实时状态为 ONLINE。
	 *
	 * <p>本次口径：接待状态只约束「承接新工作」（自动分配、手动领取、普通转交目标、
	 * 铃铛共享池提醒、队列推送）；已分配给当前坐席的会话在离线/忙碌时仍可正常收发。
	 */
	boolean isAcceptingNewWork(String tenantId, String staffId);

	/**
	 * 批量过滤出当前在线的坐席 ID（Redis MGET，一次往返）。
	 *
	 * <p>用于共享池队列推送等 fan-out 场景，避免逐个 GET 造成 N 次 Redis 往返。
	 * Redis 异常时按全部离线降级并记录日志：宁可少推，也不把排队提醒推给离线坐席。
	 */
	List<String> filterOnline(String tenantId, Collection<String> staffIds);

}
