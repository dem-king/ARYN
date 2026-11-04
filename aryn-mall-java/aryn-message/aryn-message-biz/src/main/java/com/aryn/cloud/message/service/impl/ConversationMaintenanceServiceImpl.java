package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.message.service.ConversationMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客服会话维护实现：超时关闭、待领取回收、坐席负载快照校正。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMaintenanceServiceImpl implements ConversationMaintenanceService {

	/** 超过该小时数无新消息则自动关闭（CUSTOMER_SERVICE）。 */
	private static final int INACTIVE_CLOSE_HOURS = 24;
	/** 单轮最多关闭的会话数（避免一次维护事务/消息写入过多）。 */
	private static final int CLOSE_BATCH_SIZE = 100;
	/** 单轮最多回收的待领取会话数。 */
	private static final int POOL_RECOVERY_LIMIT = 10;
	private static final String INACTIVE_CLOSE_REASON = "超过 24 小时无新消息自动关闭";
	private static final String SYSTEM_OPERATOR = "SYSTEM";

	private final MessageConversationMapper conversationMapper;
	private final MessageAgentMapper agentMapper;
	private final ConversationAssignmentService assignmentService;

	@Override
	public Map<String, Object> maintainTenant(String tenantId) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		// XxlJob 执行线程可能没有租户上下文；显式设置，保证所有带 tenant_id 的查询/写入隔离正确。
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			int closed = closeInactiveConversations(tenantId);
			int reassigned = recoverWaitingPool(tenantId);
			int repaired = repairActiveCounts(tenantId);
			Map<String, Object> stat = new LinkedHashMap<>();
			stat.put("closed", closed);
			stat.put("reassigned", reassigned);
			stat.put("repaired", repaired);
			log.info("客服会话维护完成 tenantId={}, closed={}, reassigned={}, repaired={}", tenantId, closed,
					reassigned, repaired);
			return stat;
		}
		finally {
			if (previousTenantId == null) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
		}
	}

	/**
	 * 关闭 24 小时无消息的 CUSTOMER_SERVICE 会话。
	 *
	 * <p>复用 {@link ConversationAssignmentService#close}，保证坐席负载释放、24h 重开窗口、
	 * CLOSE 审计日志与 SYSTEM 消息等语义一致。单条失败不影响其它会话。
	 */
	private int closeInactiveConversations(String tenantId) {
		LocalDateTime inactiveBefore = LocalDateTime.now().minusHours(INACTIVE_CLOSE_HOURS);
		List<MessageConversation> conversations = conversationMapper.selectInactiveConversationsByTenant(tenantId,
				inactiveBefore, CLOSE_BATCH_SIZE);
		int closed = 0;
		for (MessageConversation conversation : conversations) {
			try {
				assignmentService.close(tenantId, conversation.getId(), MessageIdentityType.SYSTEM, SYSTEM_OPERATOR,
						INACTIVE_CLOSE_REASON, true);
				closed++;
			}
			catch (RuntimeException exception) {
				log.error("自动关闭超时客服会话失败 tenantId={}, conversationId={}", tenantId, conversation.getId(),
						exception);
			}
		}
		return closed;
	}

	/** 回收共享池待领取会话；失败不阻断后续负载校正。 */
	private int recoverWaitingPool(String tenantId) {
		try {
			return assignmentService.reassignWaitingPool(tenantId, POOL_RECOVERY_LIMIT);
		}
		catch (RuntimeException exception) {
			log.error("维护任务回收待领取会话失败 tenantId={}", tenantId, exception);
			return 0;
		}
	}

	/**
	 * 按会话真实在办数（status IN ASSIGNED/ACTIVE）校正 message_agent.current_active_count。
	 *
	 * <p>历史实现只在分配/关闭时做 +1/-1，转交竞态或异常回滚会留下永久漂移；
	 * 这里以会话表为唯一事实来源对账，把快照拉回真实值（含 0 值坐席）。
	 */
	private int repairActiveCounts(String tenantId) {
		List<MessageAgent> agents = agentMapper.selectAllAgents(tenantId);
		int repaired = 0;
		for (MessageAgent agent : agents) {
			try {
				long actual = conversationMapper.countActiveByStaff(tenantId, agent.getStaffId());
				int snapshot = agent.getCurrentActiveCount() == null ? 0 : agent.getCurrentActiveCount();
				if (actual == snapshot) {
					continue;
				}
				if (agentMapper.updateActiveCount(tenantId, agent.getStaffId(), (int) actual) > 0) {
					repaired++;
					log.info("校正坐席在办会话数 tenantId={}, staffId={}, snapshot={}, actual={}", tenantId,
							agent.getStaffId(), snapshot, actual);
				}
			}
			catch (RuntimeException exception) {
				log.error("校正坐席在办会话数失败 tenantId={}, staffId={}", tenantId, agent.getStaffId(), exception);
			}
		}
		return repaired;
	}

}
