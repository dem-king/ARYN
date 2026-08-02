package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.mapper.SeckillSessionMapper;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionMapper, SeckillSession>
		implements ISeckillSessionService {

	@Override
	public List<SeckillSession> listByActivityId(String activityId) {
		return this.list(Wrappers.<SeckillSession>lambdaQuery()
				.eq(SeckillSession::getActivityId, activityId)
				.orderByAsc(SeckillSession::getStartTime));
	}

	@Override
	public void refreshStatus() {
		LocalDateTime now = LocalDateTime.now();
		// 未开始 → 进行中
		this.update(Wrappers.<SeckillSession>lambdaUpdate()
				.eq(SeckillSession::getStatus, 0)
				.le(SeckillSession::getStartTime, now)
				.gt(SeckillSession::getEndTime, now)
				.set(SeckillSession::getStatus, 1));
		// 进行中 → 已结束
		this.update(Wrappers.<SeckillSession>lambdaUpdate()
				.eq(SeckillSession::getStatus, 1)
				.le(SeckillSession::getEndTime, now)
				.set(SeckillSession::getStatus, 2));
		log.info("秒杀场次状态刷新完成, time={}", now);
	}
}