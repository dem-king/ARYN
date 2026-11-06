package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.agent.AgentConfigRequest;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.api.vo.agent.AgentCapacityVO;
import com.aryn.cloud.message.api.vo.agent.AgentRosterVO;
import com.aryn.cloud.message.api.vo.agent.AgentVO;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.message.service.event.AgentOnlineEvent;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客服坐席配置与 Redis 实时状态实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

	private static final String PRESENCE_KEY_PREFIX = "message:agent:presence:";
	private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);
	/** roster 返回上限：与 upms 人员受众/agent:candidates 的 500 上限保持一致。 */
	private static final int ROSTER_LIMIT = 500;
	/** roster 排序用的 presence 优先级：在线 > 忙碌 > 暂停 > 离线（可接置顶另由 available 控制）。 */
	private static final Map<AgentPresenceStatus, Integer> PRESENCE_ORDER = Map.of(AgentPresenceStatus.ONLINE, 0,
			AgentPresenceStatus.BUSY, 1, AgentPresenceStatus.PAUSED, 2, AgentPresenceStatus.OFFLINE, 3);

	private final MessageAgentMapper agentMapper;
	private final StringRedisTemplate redisTemplate;
	private final ApplicationEventPublisher eventPublisher;

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
	public List<AgentRosterVO> listRoster(String tenantId) {
		List<MessageAgent> agents = agentMapper.selectAllAgents(tenantId);
		if (agents == null || agents.isEmpty()) {
			// 无坐席配置时不触发 upms/Redis 调用，直接空列表（前端展示“暂无已配置坐席”）。
			return List.of();
		}
		List<MessageAgent> rosterAgents = agents.size() > ROSTER_LIMIT ? agents.subList(0, ROSTER_LIMIT) : agents;
		// 禁用坐席的 presence 一律按 OFFLINE 展示，无需读 Redis；只对启用坐席做一次 MGET。
		List<String> presenceStaffIds = rosterAgents.stream()
			.filter(agent -> CommonConstants.YES.equals(agent.getEnabled()))
			.map(MessageAgent::getStaffId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, AgentPresenceStatus> presences = batchPresence(tenantId, presenceStaffIds);
		Map<String, StaffMessageRecipientVO> staffSnapshots = loadStaffSnapshots(tenantId);
		List<AgentRosterVO> roster = new ArrayList<>(rosterAgents.size());
		for (MessageAgent agent : rosterAgents) {
			roster.add(toRoster(agent, presences, staffSnapshots));
		}
		// 可接置顶 → 在线状态 → 负载轻 → staffId，保证前端每次拿到稳定顺序。
		roster.sort(Comparator
			.comparing(AgentRosterVO::getAvailable, Comparator.nullsLast(Comparator.reverseOrder()))
			.thenComparingInt(this::presenceRank)
			.thenComparing(AgentRosterVO::getCurrentActiveCount, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(AgentRosterVO::getStaffId, Comparator.nullsLast(Comparator.naturalOrder())));
		return roster;
	}

	@Override
	public AgentCapacityVO agentCapacity(String tenantId) {
		AgentCapacityVO capacity = new AgentCapacityVO();
		List<MessageAgent> agents = agentMapper.selectAllAgents(tenantId);
		if (agents == null || agents.isEmpty()) {
			return capacity;
		}
		List<String> staffIds = agents.stream()
			.filter(agent -> CommonConstants.YES.equals(agent.getEnabled()))
			.map(MessageAgent::getStaffId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, AgentPresenceStatus> presences = batchPresence(tenantId, staffIds);
		int onlineAgentCount = 0;
		int availableCapacity = 0;
		for (MessageAgent agent : agents) {
			if (!CommonConstants.YES.equals(agent.getEnabled()) || !StringUtils.hasText(agent.getStaffId())) {
				continue;
			}
			AgentPresenceStatus presence = presences.getOrDefault(agent.getStaffId(), AgentPresenceStatus.OFFLINE);
			if (presence == AgentPresenceStatus.OFFLINE) {
				continue;
			}
			// BUSY/PAUSED 只算“在线”，不承接新工作，不计可用容量（与 isAcceptingNewWork 口径一致）。
			onlineAgentCount++;
			if (presence == AgentPresenceStatus.ONLINE) {
				availableCapacity += Math.max(maxActiveCount(agent) - activeCount(agent), 0);
			}
		}
		capacity.setOnlineAgentCount(onlineAgentCount);
		capacity.setAvailableCapacity(availableCapacity);
		return capacity;
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
		if (status == AgentPresenceStatus.ONLINE) {
			// 上线成功后发布事件，由独立监听器 best-effort 回收共享池待领取会话（异常不影响上线结果）。
			eventPublisher.publishEvent(new AgentOnlineEvent(tenantId, staffId));
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

	@Override
	public boolean isEnabledAgent(String tenantId, String staffId) {
		if (!StringUtils.hasText(staffId)) {
			return false;
		}
		MessageAgent agent = agentMapper.selectByStaffId(tenantId, staffId);
		return agent != null && CommonConstants.YES.equals(agent.getEnabled());
	}

	@Override
	public boolean isAcceptingNewWork(String tenantId, String staffId) {
		return isEnabledAgent(tenantId, staffId) && getPresence(tenantId, staffId) == AgentPresenceStatus.ONLINE;
	}

	@Override
	public List<String> filterOnline(String tenantId, Collection<String> staffIds) {
		if (staffIds == null || staffIds.isEmpty()) {
			return List.of();
		}
		List<String> candidates = staffIds.stream().filter(StringUtils::hasText).distinct().toList();
		if (candidates.isEmpty()) {
			return List.of();
		}
		// MGET 一次批量取回，避免共享池 fan-out 时逐个 GET 的 N 次往返。
		Map<String, AgentPresenceStatus> presences = batchPresence(tenantId, candidates);
		List<String> online = new ArrayList<>();
		for (String staffId : candidates) {
			if (presences.get(staffId) == AgentPresenceStatus.ONLINE) {
				online.add(staffId);
			}
		}
		return online;
	}

	/** 组装单条 roster：upms 快照只补昵称/头像；禁用坐席统一按 OFFLINE 展示。 */
	private AgentRosterVO toRoster(MessageAgent agent, Map<String, AgentPresenceStatus> presences,
			Map<String, StaffMessageRecipientVO> staffSnapshots) {
		AgentRosterVO item = new AgentRosterVO();
		item.setStaffId(agent.getStaffId());
		StaffMessageRecipientVO staff = staffSnapshots.get(agent.getStaffId());
		if (staff != null) {
			item.setStaffName(staff.getNickname());
			item.setStaffAvatar(staff.getAvatar());
		}
		boolean enabled = CommonConstants.YES.equals(agent.getEnabled());
		AgentPresenceStatus presence = enabled ? presences.getOrDefault(agent.getStaffId(), AgentPresenceStatus.OFFLINE)
				: AgentPresenceStatus.OFFLINE;
		item.setPresenceStatus(presence.name());
		item.setEnabled(agent.getEnabled());
		item.setAutoAccept(agent.getAutoAccept());
		item.setCurrentActiveCount(agent.getCurrentActiveCount());
		item.setMaxActiveCount(agent.getMaxActiveCount());
		item.setAvailable(enabled && presence == AgentPresenceStatus.ONLINE
				&& activeCount(agent) < maxActiveCount(agent));
		return item;
	}

	private int presenceRank(AgentRosterVO item) {
		try {
			return PRESENCE_ORDER.getOrDefault(AgentPresenceStatus.valueOf(item.getPresenceStatus()),
					AgentPresenceStatus.OFFLINE.ordinal());
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			return AgentPresenceStatus.OFFLINE.ordinal();
		}
	}

	/** 一次 MGET 批量读 presence：键缺失/值非法按 OFFLINE，Redis 异常按全部离线降级。 */
	private Map<String, AgentPresenceStatus> batchPresence(String tenantId, List<String> staffIds) {
		Map<String, AgentPresenceStatus> presences = new LinkedHashMap<>();
		if (staffIds == null || staffIds.isEmpty()) {
			return presences;
		}
		List<String> keys = staffIds.stream().map(staffId -> presenceKey(tenantId, staffId)).toList();
		try {
			List<String> values = redisTemplate.opsForValue().multiGet(keys);
			for (int index = 0; index < staffIds.size(); index++) {
				String value = values != null && index < values.size() ? values.get(index) : null;
				presences.put(staffIds.get(index), parsePresence(value));
			}
		}
		catch (RuntimeException exception) {
			log.warn("批量读取客服在线状态失败，按全部离线降级 tenantId={}, staffCount={}", tenantId,
					staffIds.size(), exception);
			staffIds.forEach(staffId -> presences.put(staffId, AgentPresenceStatus.OFFLINE));
		}
		return presences;
	}

	private AgentPresenceStatus parsePresence(String value) {
		if (!StringUtils.hasText(value)) {
			return AgentPresenceStatus.OFFLINE;
		}
		try {
			return AgentPresenceStatus.valueOf(value);
		}
		catch (IllegalArgumentException exception) {
			return AgentPresenceStatus.OFFLINE;
		}
	}

	/** upms 人员快照：只补昵称/头像；异常时返回空 Map（roster 仍返回 staffId，不阻塞选人）。 */
	private Map<String, StaffMessageRecipientVO> loadStaffSnapshots(String tenantId) {
		StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setLimit(ROSTER_LIMIT);
		request.setCustomerServiceOnly(true);
		try {
			StaffMessageAudiencePageVO page = staffService.queryRecipients(request);
			if (page == null || page.getRecords() == null) {
				return Map.of();
			}
			Map<String, StaffMessageRecipientVO> snapshots = new LinkedHashMap<>();
			for (StaffMessageRecipientVO recipient : page.getRecords()) {
				if (recipient != null && StringUtils.hasText(recipient.getId())) {
					snapshots.putIfAbsent(recipient.getId(), recipient);
				}
			}
			return snapshots;
		}
		catch (RuntimeException exception) {
			log.warn("读取客服人员昵称快照失败，roster 降级为仅 staffId tenantId={}", tenantId, exception);
			return Map.of();
		}
	}

	private int activeCount(MessageAgent agent) {
		return agent.getCurrentActiveCount() == null ? 0 : agent.getCurrentActiveCount();
	}

	private int maxActiveCount(MessageAgent agent) {
		return agent.getMaxActiveCount() == null ? 0 : agent.getMaxActiveCount();
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
