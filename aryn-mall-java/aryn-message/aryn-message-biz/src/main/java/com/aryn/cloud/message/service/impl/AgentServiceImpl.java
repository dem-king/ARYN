package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/** 客服坐席配置与 Redis 实时状态实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

	private static final String PRESENCE_KEY_PREFIX = "message:agent:presence:";
	private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

	private final MessageAgentMapper agentMapper;
	private final StringRedisTemplate redisTemplate;

	@DubboReference
	private final RemoteMessageStaffService staffService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AgentVO saveConfig(String tenantId, String operatorId, AgentConfigRequest request) {
		if (!staffService.isCustomerServiceStaff(tenantId, request.getStaffId())) {
			throw new ArynBusinessException("目标工作人员不具备客服资格");
		}
		MessageAgent agent = agentMapper.selectByStaffIdForUpdate(tenantId, request.getStaffId());
		LocalDateTime now = LocalDateTime.now();
		if (agent == null) {
			agent = new MessageAgent();
			agent.setId(IdWorker.getIdStr());
			agent.setTenantId(tenantId);
			agent.setStaffId(request.getStaffId());
			agent.setCurrentActiveCount(0);
			agent.setVersion(0);
			agent.setCreateBy(operatorId);
			agent.setCreateTime(now);
			agent.setDelFlag(CommonConstants.NO);
			applyConfig(agent, request);
			agentMapper.insert(agent);
		}
		else {
			if (request.getMaxActiveCount() < agent.getCurrentActiveCount()) {
				throw new ArynBusinessException("接待上限不能低于当前活跃会话数");
			}
			applyConfig(agent, request);
			agent.setUpdateBy(operatorId);
			agent.setUpdateTime(now);
			agentMapper.updateById(agent);
		}
		return toVO(agent);
	}

	@Override
	public AgentVO get(String tenantId, String staffId) {
		AgentVO agent = findConfig(tenantId, staffId);
		if (agent == null) {
			throw new ArynBusinessException("客服坐席未配置");
		}
		return agent;
	}

	@Override
	public AgentVO findConfig(String tenantId, String staffId) {
		MessageAgent agent = agentMapper.selectByStaffId(tenantId, staffId);
		return agent == null ? null : toVO(agent);
	}

	@Override
	public StaffMessageAudiencePageVO listCandidates(String tenantId) {
		StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setLimit(500);
		request.setCustomerServiceOnly(true);
		return staffService.queryRecipients(request);
	}

	@Override
	public AgentVO setPresence(String tenantId, String staffId, AgentPresenceStatus status) {
		MessageAgent agent = agentMapper.selectByStaffId(tenantId, staffId);
		if (agent == null || !CommonConstants.YES.equals(agent.getEnabled())) {
			throw new ArynBusinessException("客服坐席未启用");
		}
		try {
			if (status == AgentPresenceStatus.OFFLINE) {
				redisTemplate.delete(presenceKey(tenantId, staffId));
			}
			else {
				redisTemplate.opsForValue().set(presenceKey(tenantId, staffId), status.name(), PRESENCE_TTL);
			}
		}
		catch (RuntimeException exception) {
			log.error("更新客服实时状态失败 tenantId={}, staffId={}, status={}", tenantId, staffId, status, exception);
			throw new ArynBusinessException("客服实时状态服务暂不可用");
		}
		AgentVO vo = toVO(agent);
		vo.setPresenceStatus(status.name());
		return vo;
	}

	@Override
	public void heartbeat(String tenantId, String staffId) {
		AgentPresenceStatus presence = getPresence(tenantId, staffId);
		if (presence == AgentPresenceStatus.OFFLINE) {
			throw new ArynBusinessException("客服未上线");
		}
		try {
			redisTemplate.expire(presenceKey(tenantId, staffId), PRESENCE_TTL);
		}
		catch (RuntimeException exception) {
			log.warn("刷新客服心跳失败 tenantId={}, staffId={}", tenantId, staffId, exception);
			throw new ArynBusinessException("客服实时状态服务暂不可用");
		}
	}

	@Override
	public AgentPresenceStatus getPresence(String tenantId, String staffId) {
		try {
			String value = redisTemplate.opsForValue().get(presenceKey(tenantId, staffId));
			return value == null ? AgentPresenceStatus.OFFLINE : AgentPresenceStatus.valueOf(value);
		}
		catch (RuntimeException exception) {
			log.warn("读取客服实时状态失败，按离线降级 tenantId={}, staffId={}", tenantId, staffId, exception);
			return AgentPresenceStatus.OFFLINE;
		}
	}

	@Override
	public boolean isAutoAssignable(String tenantId, String staffId) {
		MessageAgent agent = agentMapper.selectByStaffId(tenantId, staffId);
		return agent != null && CommonConstants.YES.equals(agent.getEnabled())
				&& CommonConstants.YES.equals(agent.getAutoAccept())
				&& agent.getCurrentActiveCount() < agent.getMaxActiveCount()
				&& getPresence(tenantId, staffId) == AgentPresenceStatus.ONLINE;
	}

	private void applyConfig(MessageAgent agent, AgentConfigRequest request) {
		agent.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? CommonConstants.YES : CommonConstants.NO);
		agent.setAutoAccept(Boolean.TRUE.equals(request.getAutoAccept()) ? CommonConstants.YES : CommonConstants.NO);
		agent.setMaxActiveCount(request.getMaxActiveCount());
	}

	private AgentVO toVO(MessageAgent agent) {
		AgentVO vo = new AgentVO();
		BeanUtils.copyProperties(agent, vo);
		vo.setPresenceStatus(getPresence(agent.getTenantId(), agent.getStaffId()).name());
		return vo;
	}

	private String presenceKey(String tenantId, String staffId) {
		return PRESENCE_KEY_PREFIX + tenantId + ":" + staffId;
	}

}
