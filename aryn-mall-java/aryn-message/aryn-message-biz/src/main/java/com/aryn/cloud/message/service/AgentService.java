package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;

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

}
