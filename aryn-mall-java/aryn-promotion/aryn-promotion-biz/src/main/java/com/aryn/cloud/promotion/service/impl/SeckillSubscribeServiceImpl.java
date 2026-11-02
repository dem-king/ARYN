package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SeckillSubscribe;
import com.aryn.cloud.promotion.mapper.SeckillSubscribeMapper;
import com.aryn.cloud.promotion.service.ISeckillSubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillSubscribeServiceImpl
		extends ServiceImpl<SeckillSubscribeMapper, SeckillSubscribe>
		implements ISeckillSubscribeService {

	@Override
	public boolean subscribe(String activityId, String sessionId, String mallUserId, String templateId) {
		LocalDateTime now = LocalDateTime.now();
		SeckillSubscribe existing = findOne(sessionId, mallUserId);
		if (existing != null) {
			existing.setStatus(SeckillSubscribe.STATUS_PENDING);
			existing.setTemplateId(templateId);
			existing.setFailReason(null);
			existing.setSubscribeTime(now);
			existing.setSendTime(null);
			return this.updateById(existing);
		}
		SeckillSubscribe subscribe = new SeckillSubscribe();
		subscribe.setActivityId(activityId);
		subscribe.setSessionId(sessionId);
		subscribe.setMallUserId(mallUserId);
		subscribe.setTemplateId(templateId);
		subscribe.setStatus(SeckillSubscribe.STATUS_PENDING);
		subscribe.setSubscribeTime(now);
		try {
			return this.save(subscribe);
		}
		catch (DuplicateKeyException exception) {
			// 唯一键兜底并发重复订阅：改为复活已有行
			SeckillSubscribe concurrent = findOne(sessionId, mallUserId);
			if (concurrent == null) {
				return false;
			}
			concurrent.setStatus(SeckillSubscribe.STATUS_PENDING);
			concurrent.setTemplateId(templateId);
			concurrent.setSubscribeTime(now);
			concurrent.setSendTime(null);
			concurrent.setFailReason(null);
			return this.updateById(concurrent);
		}
	}

	@Override
	public boolean unsubscribe(String sessionId, String mallUserId) {
		SeckillSubscribe existing = findOne(sessionId, mallUserId);
		if (existing == null) {
			return true;
		}
		// 已投递的提醒不再撤回：微信订阅消息一旦发送无法取消
		if (existing.getStatus() != null && existing.getStatus() == SeckillSubscribe.STATUS_SENT) {
			return false;
		}
		return this.update(Wrappers.<SeckillSubscribe>lambdaUpdate()
				.eq(SeckillSubscribe::getId, existing.getId())
				.set(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_CANCELLED));
	}

	@Override
	public boolean isSubscribed(String sessionId, String mallUserId) {
		return this.count(Wrappers.<SeckillSubscribe>lambdaQuery()
				.eq(SeckillSubscribe::getSessionId, sessionId)
				.eq(SeckillSubscribe::getMallUserId, mallUserId)
				.in(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_PENDING,
						SeckillSubscribe.STATUS_SENT)) > 0;
	}

	@Override
	public Set<String> subscribedSessionIds(List<String> sessionIds, String mallUserId) {
		if (CollectionUtils.isEmpty(sessionIds) || mallUserId == null) {
			return Set.of();
		}
		return this.list(Wrappers.<SeckillSubscribe>lambdaQuery()
				.select(SeckillSubscribe::getSessionId)
				.in(SeckillSubscribe::getSessionId, sessionIds)
				.eq(SeckillSubscribe::getMallUserId, mallUserId)
				.in(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_PENDING,
						SeckillSubscribe.STATUS_SENT))
				.stream()
				.map(SeckillSubscribe::getSessionId)
				.collect(Collectors.toSet());
	}

	@Override
	public List<SeckillSubscribe> listPendingBySession(String sessionId) {
		return this.list(Wrappers.<SeckillSubscribe>lambdaQuery()
				.eq(SeckillSubscribe::getSessionId, sessionId)
				.eq(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_PENDING)
				.orderByAsc(SeckillSubscribe::getSubscribeTime));
	}

	@Override
	public boolean claim(String id) {
		return this.update(Wrappers.<SeckillSubscribe>lambdaUpdate()
				.eq(SeckillSubscribe::getId, id)
				.eq(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_PENDING)
				.set(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_SENT)
				.set(SeckillSubscribe::getSendTime, LocalDateTime.now()));
	}

	@Override
	public void markFailed(String id, String reason) {
		this.update(Wrappers.<SeckillSubscribe>lambdaUpdate()
				.eq(SeckillSubscribe::getId, id)
				.set(SeckillSubscribe::getStatus, SeckillSubscribe.STATUS_FAILED)
				.set(SeckillSubscribe::getFailReason, reason == null ? "发送失败" : truncate(reason)));
	}

	private SeckillSubscribe findOne(String sessionId, String mallUserId) {
		List<SeckillSubscribe> list = this.list(Wrappers.<SeckillSubscribe>lambdaQuery()
				.eq(SeckillSubscribe::getSessionId, sessionId)
				.eq(SeckillSubscribe::getMallUserId, mallUserId));
		return list.isEmpty() ? null : list.get(0);
	}

	private String truncate(String text) {
		return text.length() > 255 ? text.substring(0, 255) : text;
	}
}
