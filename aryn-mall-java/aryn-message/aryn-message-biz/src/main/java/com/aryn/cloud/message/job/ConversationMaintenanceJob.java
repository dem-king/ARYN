package com.aryn.cloud.message.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 自动关闭长期无消息的客服会话。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMaintenanceJob {

	private static final int BATCH_SIZE = 100;

	private final MessageConversationMapper conversationMapper;
	private final ConversationAssignmentService assignmentService;

	@XxlJob("conversationMaintenanceJob")
	public void maintain() {
		List<MessageConversation> conversations = conversationMapper
			.selectInactiveConversations(LocalDateTime.now().minusHours(24), BATCH_SIZE);
		for (MessageConversation conversation : conversations) {
			try {
				ArynTenantContextHolder.setTenantId(conversation.getTenantId());
				assignmentService.close(conversation.getTenantId(), conversation.getId(), MessageIdentityType.SYSTEM,
						"SYSTEM", "超过 24 小时无新消息自动关闭", true);
			}
			catch (Exception exception) {
				log.error("自动关闭客服会话失败 tenantId={}, conversationId={}", conversation.getTenantId(),
						conversation.getId(), exception);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

}
