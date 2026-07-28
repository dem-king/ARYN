package com.aryn.cloud.message.job;

import com.aryn.cloud.message.api.entity.MessageChannelTask;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.mapper.MessageChannelTaskMapper;
import com.aryn.cloud.message.service.impl.WechatSubscribeChannelService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 恢复到期的外部消息通道任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChannelRecoveryJob {

	private static final int RECOVERY_LIMIT = 100;

	private final MessageChannelTaskMapper taskMapper;
	private final WechatSubscribeChannelService wechatSubscribeChannelService;

	@XxlJob("messageChannelRecoveryJob")
	public void recover() {
		LocalDateTime now = LocalDateTime.now();
		taskMapper.resetStaleSending(now.minusMinutes(5), now);
		for (MessageChannelTask task : taskMapper.selectDueTasks(now, RECOVERY_LIMIT)) {
			try {
				ArynTenantContextHolder.setTenantId(task.getTenantId());
				wechatSubscribeChannelService.dispatch(task);
			}
			catch (RuntimeException exception) {
				log.error("恢复外部消息通道任务失败 tenantId={}, taskId={}", task.getTenantId(), task.getId(), exception);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}
}
