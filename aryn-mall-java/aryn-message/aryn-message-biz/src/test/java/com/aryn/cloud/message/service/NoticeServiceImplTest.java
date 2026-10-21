package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.api.vo.notice.NoticeRecipientItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeRecipientStatsVO;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.impl.NoticeServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceImplTest {

	private MessageNoticeMapper noticeMapper;
	private MessageDispatchTaskMapper taskMapper;
	private MessageRecipientMapper recipientMapper;
	private NoticeDispatchService dispatchService;
	private NoticeServiceImpl service;

	@BeforeEach
	void setUp() {
		noticeMapper = mock(MessageNoticeMapper.class);
		taskMapper = mock(MessageDispatchTaskMapper.class);
		recipientMapper = mock(MessageRecipientMapper.class);
		dispatchService = mock(NoticeDispatchService.class);
		service = new NoticeServiceImpl(noticeMapper, taskMapper, recipientMapper, dispatchService, new ObjectMapper());
	}

	@Test
	void draftCanBeEdited() {
		MessageNotice draft = notice(NoticeStatus.DRAFT);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(draft);

		service.updateDraft("tenant-1", "staff-1", "notice-1", request(Set.of(MessageIdentityType.MALL_USER)));

		verify(noticeMapper).updateById(draft);
		assertEquals("新标题", draft.getTitle());
	}

	@Test
	void publishedNoticeCannotBeEdited() {
		MessageNotice published = notice(NoticeStatus.PUBLISHED);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(published);

		assertThrows(ArynBusinessException.class,
				() -> service.updateDraft("tenant-1", "staff-1", "notice-1",
						request(Set.of(MessageIdentityType.MALL_USER))));
		verify(noticeMapper, never()).updateById(any(MessageNotice.class));
	}

	@Test
	void publishCreatesOneTaskForEachTargetType() {
		MessageNotice draft = notice(NoticeStatus.DRAFT);
		draft.setTargetTypes("MALL_USER,SYS_USER");
		draft.setAudienceSnapshot("{}");
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(draft);

		service.publish("tenant-1", "staff-1", "notice-1");

		ArgumentCaptor<MessageDispatchTask> captor = ArgumentCaptor.forClass(MessageDispatchTask.class);
		verify(taskMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
		assertEquals(Set.of("MALL_USER", "SYS_USER"),
				captor.getAllValues().stream().map(MessageDispatchTask::getTargetType).collect(java.util.stream.Collectors.toSet()));
		verify(dispatchService, org.mockito.Mockito.times(2)).dispatch(org.mockito.ArgumentMatchers.eq("tenant-1"),
				org.mockito.ArgumentMatchers.anyString());
		assertEquals(NoticeStatus.PUBLISHED.name(), draft.getStatus());
	}

	@Test
	void revokeKeepsNoticeHistory() {
		MessageNotice published = notice(NoticeStatus.PUBLISHED);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(published);

		service.revoke("tenant-1", "staff-1", "notice-1");

		verify(noticeMapper).updateById(published);
		verify(noticeMapper, never()).deleteById(any());
		verify(taskMapper).cancelPendingTasks("tenant-1", "notice-1", "staff-1");
		assertEquals(NoticeStatus.REVOKED.name(), published.getStatus());
	}

	@Test
	void republishRestoresNoticeAndRedispatchesUnfinishedTasks() {
		MessageNotice revoked = notice(NoticeStatus.REVOKED);
		revoked.setExpireTime(LocalDateTime.now().plusDays(1));
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(revoked);
		MessageDispatchTask canceled = task("task-canceled", DispatchTaskStatus.CANCELED);
		MessageDispatchTask succeeded = task("task-succeeded", DispatchTaskStatus.SUCCEEDED);
		when(taskMapper.selectByMessageId("tenant-1", "notice-1"))
			.thenReturn(List.of(canceled, succeeded));
		when(taskMapper.resetTasksForRepublish("tenant-1", List.of("task-canceled"), "staff-1")).thenReturn(1);

		service.republish("tenant-1", "staff-1", "notice-1");

		assertEquals(NoticeStatus.PUBLISHED.name(), revoked.getStatus());
		verify(noticeMapper).updateById(revoked);
		verify(taskMapper).resetTasksForRepublish("tenant-1", List.of("task-canceled"), "staff-1");
		verify(dispatchService).dispatch("tenant-1", "task-canceled");
		verify(dispatchService, never()).dispatch("tenant-1", "task-succeeded");
	}

	@Test
	void republishExpiredNoticeIsRejected() {
		MessageNotice revoked = notice(NoticeStatus.REVOKED);
		revoked.setExpireTime(LocalDateTime.now().minusMinutes(1));
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(revoked);

		assertThrows(ArynBusinessException.class,
				() -> service.republish("tenant-1", "staff-1", "notice-1"));
		verify(noticeMapper, never()).updateById(revoked);
	}

	@Test
	void republishRejectedWhileDispatchRunning() {
		MessageNotice revoked = notice(NoticeStatus.REVOKED);
		revoked.setExpireTime(LocalDateTime.now().plusDays(1));
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(revoked);
		when(taskMapper.selectByMessageId("tenant-1", "notice-1"))
			.thenReturn(List.of(task("task-running", DispatchTaskStatus.RUNNING)));

		assertThrows(ArynBusinessException.class,
				() -> service.republish("tenant-1", "staff-1", "notice-1"));
		verify(noticeMapper, never()).updateById(revoked);
	}

	@SuppressWarnings("unchecked")
	@Test
	void recipientsArePagedWithinNoticeAndTenant() {
		when(noticeMapper.selectOne(any())).thenReturn(notice(NoticeStatus.PUBLISHED));
		MessageRecipient recipient = new MessageRecipient();
		recipient.setId("recipient-1");
		recipient.setMessageId("notice-1");
		recipient.setRecipientType("SYS_USER");
		recipient.setRecipientId("staff-9");
		recipient.setRecipientName("张三");
		recipient.setReadStatus("1");
		recipient.setInboxStatus("VISIBLE");
		Page<MessageRecipient> source = new Page<>(1, 20);
		source.setRecords(List.of(recipient));
		source.setTotal(1);
		when(recipientMapper.selectPage(any(Page.class), any())).thenReturn(source);

		IPage<NoticeRecipientItemVO> page = service.recipients("tenant-1", "notice-1", "1", "SYS_USER", null,
				new Page<>(1, 20));

		NoticeRecipientItemVO vo = page.getRecords().get(0);
		assertEquals("recipient-1", vo.getRecipientRecordId());
		assertEquals("staff-9", vo.getRecipientId());
		assertEquals("张三", vo.getRecipientName());
		assertEquals("1", vo.getReadStatus());
	}

	@Test
	void inboxMutationsAreBoundToCurrentIdentity() {
		when(recipientMapper.markRead("tenant-1", "MALL_USER", "member-1", "notice-1")).thenReturn(1);
		when(recipientMapper.hide("tenant-1", "MALL_USER", "member-1", "notice-1")).thenReturn(1);

		service.markRead("tenant-1", MessageIdentityType.MALL_USER, "member-1", "notice-1");
		service.markAllRead("tenant-1", MessageIdentityType.MALL_USER, "member-1");
		service.hide("tenant-1", MessageIdentityType.MALL_USER, "member-1", "notice-1");
		service.inbox("tenant-1", MessageIdentityType.MALL_USER, "member-1", new NoticeInboxQuery());

		verify(recipientMapper).markRead("tenant-1", "MALL_USER", "member-1", "notice-1");
		verify(recipientMapper).markAllRead("tenant-1", "MALL_USER", "member-1");
		verify(recipientMapper).hide("tenant-1", "MALL_USER", "member-1", "notice-1");
		verify(recipientMapper).selectInbox("tenant-1", "MALL_USER", "member-1", null, 21);
	}

	@Test
	void deleteDraftOnlyAcceptsDraft() {
		MessageNotice draft = notice(NoticeStatus.DRAFT);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(draft);
		when(noticeMapper.logicDeleteDraft("tenant-1", "notice-1", "staff-1")).thenReturn(1);

		service.deleteDraft("tenant-1", "staff-1", "notice-1");

		verify(noticeMapper).logicDeleteDraft("tenant-1", "notice-1", "staff-1");
	}

	@Test
	void deletePublishedNoticeIsRejected() {
		MessageNotice published = notice(NoticeStatus.PUBLISHED);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(published);

		assertThrows(ArynBusinessException.class,
				() -> service.deleteDraft("tenant-1", "staff-1", "notice-1"));
		verify(noticeMapper, never()).logicDeleteDraft(anyString(), anyString(), anyString());
	}

	@Test
	void retryDispatchResetsOnlyFailedTasks() {
		MessageNotice published = notice(NoticeStatus.PUBLISHED);
		when(noticeMapper.selectByIdForUpdate("tenant-1", "notice-1")).thenReturn(published);
		MessageDispatchTask failed = task("task-failed", DispatchTaskStatus.FAILED);
		MessageDispatchTask partial = task("task-partial", DispatchTaskStatus.PARTIAL_FAILED);
		MessageDispatchTask succeeded = task("task-succeeded", DispatchTaskStatus.SUCCEEDED);
		when(taskMapper.selectByMessageId("tenant-1", "notice-1"))
			.thenReturn(List.of(failed, partial, succeeded));
		when(taskMapper.resetFailedTasks("tenant-1", List.of("task-failed", "task-partial"), "staff-1"))
			.thenReturn(2);

		service.retryDispatch("tenant-1", "staff-1", "notice-1");

		verify(taskMapper).resetFailedTasks("tenant-1", List.of("task-failed", "task-partial"), "staff-1");
		verify(taskMapper, never()).updateById(succeeded);
		verify(dispatchService).dispatch("tenant-1", "task-failed");
		verify(dispatchService).dispatch("tenant-1", "task-partial");
	}

	@SuppressWarnings("unchecked")
	@Test
	void pageEnrichesDispatchAndReadStats() {
		MessageNotice published = notice(NoticeStatus.PUBLISHED);
		Page<MessageNotice> source = new Page<>(1, 10);
		source.setRecords(List.of(published));
		source.setTotal(1);
		when(noticeMapper.selectPage(any(Page.class), any())).thenReturn(source);
		when(taskMapper.selectByMessageIds("tenant-1", List.of("notice-1")))
			.thenReturn(List.of(task("task-1", DispatchTaskStatus.SUCCEEDED)));
		NoticeRecipientStatsVO stats = new NoticeRecipientStatsVO();
		stats.setMessageId("notice-1");
		stats.setRecipientCount(12L);
		stats.setReadCount(7L);
		stats.setHiddenCount(2L);
		when(recipientMapper.selectStatsByMessageIds("tenant-1", List.of("notice-1")))
			.thenReturn(List.of(stats));

		IPage<NoticeVO> page = service.page("tenant-1", null, null, new Page<>(1, 10));

		NoticeVO vo = page.getRecords().get(0);
		assertEquals("SUCCEEDED", vo.getDispatchStatus());
		assertEquals(1, vo.getDispatchTasks().size());
		assertEquals(12L, vo.getRecipientCount());
		assertEquals(7L, vo.getReadCount());
		assertEquals(2L, vo.getHiddenCount());
	}

	@Test
	void arbitraryExternalUrlIsRejected() {
		NoticeSaveRequest request = request(Set.of(MessageIdentityType.MALL_USER));
		request.setJumpType(NoticeJumpType.INTERNAL_ROUTE);
		request.setJumpPayload("{\"url\":\"https://example.com\"}");

		assertThrows(ArynBusinessException.class,
				() -> service.createDraft("tenant-1", "staff-1", "客服", request));
		verify(noticeMapper, never()).insert(any(MessageNotice.class));
	}

	private MessageDispatchTask task(String id, DispatchTaskStatus status) {
		MessageDispatchTask task = new MessageDispatchTask();
		task.setId(id);
		task.setMessageId("notice-1");
		task.setTargetType(MessageIdentityType.SYS_USER.name());
		task.setAudienceType("ALL");
		task.setStatus(status.name());
		task.setRetryCount(3);
		return task;
	}

	private MessageNotice notice(NoticeStatus status) {
		MessageNotice notice = new MessageNotice();
		notice.setId("notice-1");
		notice.setTenantId("tenant-1");
		notice.setStatus(status.name());
		notice.setTargetTypes("MALL_USER");
		notice.setAudienceSnapshot("{}");
		return notice;
	}

	private NoticeSaveRequest request(Set<MessageIdentityType> targetTypes) {
		NoticeSaveRequest request = new NoticeSaveRequest();
		request.setTitle("新标题");
		request.setContent("纯文本内容");
		request.setCategory("SYSTEM");
		request.setTargetTypes(targetTypes);
		request.setJumpType(NoticeJumpType.NONE);
		return request;
	}

}
