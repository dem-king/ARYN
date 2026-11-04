package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.impl.NoticeDispatchServiceImpl;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeDispatchServiceImplTest {

	@Test
	void repeatedDispatchDoesNotInsertRecipientsAgain() {
		MessageDispatchTaskMapper taskMapper = mock(MessageDispatchTaskMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		RemoteMessageAudienceService audienceService = mock(RemoteMessageAudienceService.class);
		RemoteMessageStaffService staffService = mock(RemoteMessageStaffService.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		when(noticeMapper.selectStatus("tenant-1", "notice-1")).thenReturn("PUBLISHED");
		UserMessageRecipientVO member = new UserMessageRecipientVO();
		member.setId("member-1");
		member.setNickname("会员");
		UserMessageAudiencePageVO page = new UserMessageAudiencePageVO();
		page.setRecords(List.of(member));
		page.setHasMore(false);
		when(audienceService.queryRecipients(any())).thenReturn(page);
		when(recipientMapper.insertIgnoreBatch(any())).thenReturn(1);
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper, noticeMapper,
				new ObjectMapper(), transactionTemplate, audienceService, staffService);

		service.dispatch("tenant-1", "task-1");
		service.dispatch("tenant-1", "task-1");

		verify(recipientMapper, times(1)).insertIgnoreBatch(any());
		verify(audienceService, times(1)).queryRecipients(any());
		assertEquals(DispatchTaskStatus.SUCCEEDED.name(), task.getStatus());
	}

	@Test
	void unsupportedTargetEventuallyFailsWithoutLeakingRecipients() {
		MessageDispatchTaskMapper taskMapper = mock(MessageDispatchTaskMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		task.setTargetType("SYSTEM");
		task.setRetryCount(2);
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		when(noticeMapper.selectStatus("tenant-1", "notice-1")).thenReturn("PUBLISHED");
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper, noticeMapper,
				new ObjectMapper(), transactionTemplate, mock(RemoteMessageAudienceService.class),
				mock(RemoteMessageStaffService.class));

		service.dispatch("tenant-1", "task-1");

		assertEquals(DispatchTaskStatus.FAILED.name(), task.getStatus());
		verify(recipientMapper, never()).insertIgnoreBatch(any());
	}

	@Test
	void revokedNoticeCancelsTaskBeforeDispatch() {
		MessageDispatchTaskMapper taskMapper = mock(MessageDispatchTaskMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		when(noticeMapper.selectStatus("tenant-1", "notice-1")).thenReturn("REVOKED");
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper, noticeMapper,
				new ObjectMapper(), transactionTemplate, mock(RemoteMessageAudienceService.class),
				mock(RemoteMessageStaffService.class));

		service.dispatch("tenant-1", "task-1");

		verify(taskMapper).markCanceled("tenant-1", "task-1");
		verify(recipientMapper, never()).insertIgnoreBatch(any());
	}

	@Test
	void revokeDuringDispatchStopsBeforeInsert() {
		MessageDispatchTaskMapper taskMapper = mock(MessageDispatchTaskMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		RemoteMessageAudienceService audienceService = mock(RemoteMessageAudienceService.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		when(noticeMapper.selectStatus("tenant-1", "notice-1")).thenReturn("PUBLISHED", "REVOKED");
		UserMessageAudiencePageVO page = new UserMessageAudiencePageVO();
		page.setRecords(List.of(member()));
		page.setHasMore(false);
		when(audienceService.queryRecipients(any())).thenReturn(page);
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper, noticeMapper,
				new ObjectMapper(), transactionTemplate, audienceService, mock(RemoteMessageStaffService.class));

		service.dispatch("tenant-1", "task-1");

		verify(taskMapper).markCanceled("tenant-1", "task-1");
		verify(recipientMapper, never()).insertIgnoreBatch(any());
	}

	@Test
	void recoverTenantOnlyRecoversStaleTasksOfThatTenant() {
		MessageDispatchTaskMapper taskMapper = mock(MessageDispatchTaskMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		when(taskMapper.selectRecoveryTasksByTenant(org.mockito.ArgumentMatchers.eq("tenant-1"), any(),
				org.mockito.ArgumentMatchers.eq(100))).thenReturn(List.of(task));
		NoticeDispatchServiceImpl service = org.mockito.Mockito.spy(new NoticeDispatchServiceImpl(taskMapper,
				recipientMapper, noticeMapper, new ObjectMapper(), transactionTemplate,
				mock(RemoteMessageAudienceService.class), mock(RemoteMessageStaffService.class)));
		org.mockito.Mockito.doNothing().when(service).dispatch("tenant-1", "task-1");

		int recovered = service.recoverTenant("tenant-1", 100);

		assertEquals(1, recovered);
		// 租户过滤必须走带 tenantId 的查询，不能复用跨租户扫描。
		verify(taskMapper).selectRecoveryTasksByTenant(org.mockito.ArgumentMatchers.eq("tenant-1"), any(),
				org.mockito.ArgumentMatchers.eq(100));
		verify(service).dispatch("tenant-1", "task-1");
	}

	private UserMessageRecipientVO member() {
		UserMessageRecipientVO member = new UserMessageRecipientVO();
		member.setId("member-1");
		member.setNickname("会员");
		return member;
	}

	private MessageDispatchTask task() {
		MessageDispatchTask task = new MessageDispatchTask();
		task.setId("task-1");
		task.setTenantId("tenant-1");
		task.setMessageId("notice-1");
		task.setTargetType("MALL_USER");
		task.setAudienceCondition("{}");
		task.setStatus(DispatchTaskStatus.PENDING.name());
		task.setRetryCount(0);
		task.setSuccessCount(0L);
		task.setFailureCount(0L);
		return task;
	}

}
