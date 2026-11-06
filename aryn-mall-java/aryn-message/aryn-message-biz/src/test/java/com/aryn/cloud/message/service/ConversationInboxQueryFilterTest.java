package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationServiceImpl;
import com.aryn.cloud.message.service.impl.CustomerSnapshotService;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-A 会话列表筛选参数（keyword/statuses/assignedStaffId/queueCode/unreadOnly）契约测试。
 *
 * <p>锁住服务层的归一化与优先级语义：statuses 覆盖旧 status、keyword 去空白并做 LIKE 转义、
 * 空精确条件归 null、limit 归一化、staff/admin 两端口径一致；不依赖真实数据库。</p>
 */
class ConversationInboxQueryFilterTest {

	private MessageConversationMapper conversationMapper;
	private AgentService agentService;
	private ConversationServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		agentService = mock(AgentService.class);
		service = new ConversationServiceImpl(conversationMapper, mock(MessageParticipantMapper.class),
				mock(MessageAssignmentLogMapper.class), new ObjectMapper(), mock(ConversationAssignmentService.class),
				agentService, mock(RemoteMessageAudienceService.class), mock(CustomerSnapshotService.class));
		stubEmptyInbox();
	}

	@Test
	void keywordIsTrimmedAndLikeEscapedBeforeMapper() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setKeyword(" 50%_off! ");

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null, null, "50!%!_off!!",
				null, null, false, null, null, 21);
	}

	@Test
	void statusesTakePrecedenceOverLegacyStatusAndAreNormalized() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatus("OPEN");
		query.setStatuses(List.of(" waiting ", "ACTIVE", "WAITING", ""));

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		// statuses 非空时旧页签 status 被忽略；集合 trim + 大写 + 去重。
		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null, List.of("WAITING", "ACTIVE"),
				null, null, null, false, null, null, 21);
	}

	@Test
	void legacyStatusStillAppliesWhenStatusesAbsent() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatus("open");

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", "OPEN", null, null, null, null,
				false, null, null, 21);
	}

	@Test
	void invalidStatusesAndOversizedKeywordAreRejected() {
		ConversationInboxQuery badStatuses = new ConversationInboxQuery();
		// 状态集合只接受精确状态值，OPEN/CLOSED 属于旧页签语义。
		badStatuses.setStatuses(List.of("OPEN"));
		assertThrows(ArynBusinessException.class,
				() -> service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", badStatuses));

		ConversationInboxQuery longKeyword = new ConversationInboxQuery();
		longKeyword.setKeyword("k".repeat(65));
		assertThrows(ArynBusinessException.class,
				() -> service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", longKeyword));

		verify(conversationMapper, never()).selectInbox(any(), any(), any(), any(), any(), any(), any(), any(),
				anyBoolean(), any(), any(), anyInt());
	}

	@Test
	void blankPreciseFiltersAreTreatedAsAbsent() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setKeyword("   ");
		query.setAssignedStaffId("  ");
		query.setQueueCode("  ");
		query.setUnreadOnly(null);

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null, null, null, null, null,
				false, null, null, 21);
	}

	@Test
	void preciseFiltersAndUnreadOnlyReachMapper() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatuses(List.of("WAITING", "ASSIGNED"));
		query.setAssignedStaffId(" staff-9 ");
		query.setQueueCode(" VIP ");
		query.setUnreadOnly(true);
		query.setLimit(50);

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null,
				List.of("WAITING", "ASSIGNED"), null, "staff-9", "VIP", true, null, null, 51);
	}

	@Test
	void limitIsClampedToOneHundredForBothViews() {
		ConversationInboxQuery oversized = new ConversationInboxQuery();
		oversized.setLimit(1000);

		service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", oversized);

		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null, null, null, null, null,
				false, null, null, 101);

		ConversationInboxQuery nonPositive = new ConversationInboxQuery();
		nonPositive.setLimit(0);

		service.adminInbox("tenant-1", nonPositive);

		verify(conversationMapper).selectTenantInbox("tenant-1", null, null, null, null, null, false, null, null,
				21);
	}

	@Test
	void adminInboxPassesTenantWideFiltersToMapper() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatuses(List.of("WAITING"));
		query.setKeyword("王小雅");
		query.setAssignedStaffId("staff-1");
		query.setQueueCode("DEFAULT");
		query.setUnreadOnly(true);
		query.setLimit(10);

		ConversationInboxPageVO page = service.adminInbox("tenant-1", query);

		assertTrue(page.getRecords().isEmpty());
		assertFalse(page.isHasMore());
		verify(conversationMapper).selectTenantInbox("tenant-1", null, List.of("WAITING"), "王小雅", "staff-1",
				"DEFAULT", true, null, null, 11);
	}

	@Test
	void malformedCursorIsRejectedBeforeAnyQuery() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setCursor("not-a-valid-cursor");

		assertThrows(ArynBusinessException.class,
				() -> service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query));

		verify(conversationMapper, never()).selectInbox(any(), any(), any(), any(), any(), any(), any(), any(),
				anyBoolean(), any(), any(), anyInt());
	}

	@Test
	void nullQueryUsesDefaults() {
		ConversationInboxPageVO page = service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", null);

		assertNotNull(page);
		assertTrue(page.getRecords().isEmpty());
		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", null, null, null, null, null,
				false, null, null, 21);
	}

	private void stubEmptyInbox() {
		when(conversationMapper.selectInbox(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
				any(), any(), anyInt())).thenReturn(List.of());
		when(conversationMapper.selectTenantInbox(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(),
				anyInt())).thenReturn(List.of());
	}

}
