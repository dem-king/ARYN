package com.aryn.cloud.pay.job;

import com.aryn.cloud.pay.service.PayEventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付事件 outbox 重投任务。多实例同时运行也通过行级抢占避免重复发送；
 * 消费端仍必须按事件/业务键幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayEventOutboxRelayJob {

	private final PayEventOutboxService outboxService;

	@Scheduled(fixedDelayString = "${aryn.pay.outbox.relay-interval-ms:5000}",
			initialDelayString = "${aryn.pay.outbox.relay-initial-delay-ms:10000}")
	public void relay() {
		try {
			int sent = outboxService.relayBatch();
			if (sent > 0) {
				log.info("支付事件 outbox 重投完成，sent={}", sent);
			}
		}
		catch (Exception exception) {
			log.warn("支付事件 outbox 重投任务异常: {}", exception.getMessage());
		}
	}

}
