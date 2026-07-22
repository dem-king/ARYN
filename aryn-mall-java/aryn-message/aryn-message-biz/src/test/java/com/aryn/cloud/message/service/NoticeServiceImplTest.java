package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.impl.NoticeServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
		assertEquals(NoticeStatus.REVOKED.name(), published.getStatus());
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
	void arbitraryExternalUrlIsRejected() {
		NoticeSaveRequest request = request(Set.of(MessageIdentityType.MALL_USER));
		request.setJumpType(NoticeJumpType.INTERNAL_ROUTE);
		request.setJumpPayload("{\"url\":\"https://example.com\"}");

		assertThrows(ArynBusinessException.class,
				() -> service.createDraft("tenant-1", "staff-1", "客服", request));
		verify(noticeMapper, never()).insert(any(MessageNotice.class));
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
