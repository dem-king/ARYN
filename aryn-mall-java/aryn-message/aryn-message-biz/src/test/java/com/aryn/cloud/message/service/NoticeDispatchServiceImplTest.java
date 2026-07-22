package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
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
		RemoteMessageAudienceService audienceService = mock(RemoteMessageAudienceService.class);
		RemoteMessageStaffService staffService = mock(RemoteMessageStaffService.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		UserMessageRecipientVO member = new UserMessageRecipientVO();
		member.setId("member-1");
		member.setNickname("会员");
		UserMessageAudiencePageVO page = new UserMessageAudiencePageVO();
		page.setRecords(List.of(member));
		page.setHasMore(false);
		when(audienceService.queryRecipients(any())).thenReturn(page);
		when(recipientMapper.insertIgnoreBatch(any())).thenReturn(1);
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper,
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
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		MessageDispatchTask task = task();
		task.setTargetType("SYSTEM");
		task.setRetryCount(2);
		when(taskMapper.selectByIdForUpdate("tenant-1", "task-1")).thenReturn(task);
		NoticeDispatchServiceImpl service = new NoticeDispatchServiceImpl(taskMapper, recipientMapper,
				new ObjectMapper(), transactionTemplate, mock(RemoteMessageAudienceService.class),
				mock(RemoteMessageStaffService.class));

		service.dispatch("tenant-1", "task-1");

		assertEquals(DispatchTaskStatus.FAILED.name(), task.getStatus());
		verify(recipientMapper, never()).insertIgnoreBatch(any());
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
