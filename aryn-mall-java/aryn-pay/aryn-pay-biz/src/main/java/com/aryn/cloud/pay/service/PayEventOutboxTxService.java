package com.aryn.cloud.pay.service;

import com.aryn.cloud.pay.mapper.PayEventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * outbox 短事务边界：抢占/标记单条事件，不在事务内做 MQ 网络发送。
 */
@Service
@RequiredArgsConstructor
public class PayEventOutboxTxService {

	private final PayEventOutboxMapper outboxMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int resetStaleSending(LocalDateTime before) {
		return outboxMapper.resetStaleSending(before);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public boolean claim(String id) {
		return outboxMapper.claim(id) == 1;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markSent(String id) {
		outboxMapper.markSent(id);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markRetry(String id, String status, LocalDateTime nextRetryTime, String lastError) {
		outboxMapper.markRetry(id, status, nextRetryTime, lastError);
	}

}
