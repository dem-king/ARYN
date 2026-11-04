package com.aryn.cloud.message.listener;

import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.message.service.event.AgentOnlineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 坐席上线后回收一轮共享池待领取会话。
 *
 * <p>产品决策：不做「自动上线」，上线仍是显式动作；但坐席上线时 best-effort 回收待领取会话，
 * 让此前因无人在线而滞留排队的会话尽快落到刚上线的坐席。失败只记日志，绝不阻断上线接口。</p>
 *
 * <p>监听器单独成类而不是在 {@link com.aryn.cloud.message.service.impl.AgentServiceImpl} 内直接调用，
 * 是为了避免 AgentService ↔ ConversationAssignmentService 的构造器循环依赖。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOnlineEventListener {

	/** 单次上线回收的待领取会话上限（契约值，避免上线接口被大量积压拖慢）。 */
	private static final int POOL_RECOVERY_LIMIT = 10;

	private final ConversationAssignmentService assignmentService;

	@EventListener
	public void onAgentOnline(AgentOnlineEvent event) {
		try {
			int reassigned = assignmentService.reassignWaitingPool(event.tenantId(), POOL_RECOVERY_LIMIT);
			log.info("坐席上线回收共享池完成 tenantId={}, staffId={}, reassigned={}", event.tenantId(), event.staffId(),
					reassigned);
		}
		catch (RuntimeException exception) {
			log.warn("坐席上线回收共享池失败（best-effort，不影响上线结果） tenantId={}, staffId={}", event.tenantId(),
					event.staffId(), exception);
		}
	}

}
