package com.aryn.cloud.message.job;

import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.service.NoticeDispatchService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 恢复超时通知分发任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeDispatchRecoveryJob {

	private static final int RECOVERY_LIMIT = 100;

	private final MessageDispatchTaskMapper taskMapper;
	private final NoticeDispatchService dispatchService;

	@XxlJob("noticeDispatchRecoveryJob")
	public void recover() {
		List<MessageDispatchTask> tasks = taskMapper.selectRecoveryTasks(LocalDateTime.now().minusMinutes(5),
				RECOVERY_LIMIT);
		for (MessageDispatchTask task : tasks) {
			try {
				dispatchService.dispatch(task.getTenantId(), task.getId());
			}
			catch (Exception exception) {
				log.error("恢复通知分发任务失败 tenantId={}, taskId={}", task.getTenantId(), task.getId(), exception);
			}
		}
	}

}
