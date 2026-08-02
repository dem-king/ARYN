package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.mapper.SeckillGoodsMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillGoodsServiceImpl extends ServiceImpl<SeckillGoodsMapper, SeckillGoods>
		implements ISeckillGoodsService {

	private final ISeckillSessionService seckillSessionService;

	@Override
	public List<SeckillGoods> listBySessionId(String sessionId) {
		return this.list(Wrappers.<SeckillGoods>lambdaQuery()
				.eq(SeckillGoods::getSessionId, sessionId));
	}

	@Override
	public SeckillGoods getBySkuId(String skuId) {
		LocalDateTime now = LocalDateTime.now();
		// 查询当前进行中场次下的秒杀商品
		List<SeckillSession> activeSessions = seckillSessionService.list(Wrappers.<SeckillSession>lambdaQuery()
				.eq(SeckillSession::getStatus, 1)
				.le(SeckillSession::getStartTime, now)
				.gt(SeckillSession::getEndTime, now));
		if (activeSessions.isEmpty()) {
			return null;
		}
		List<String> sessionIds = activeSessions.stream().map(SeckillSession::getId).toList();
		return this.getOne(Wrappers.<SeckillGoods>lambdaQuery()
				.eq(SeckillGoods::getSkuId, skuId)
				.in(SeckillGoods::getSessionId, sessionIds)
				.last("LIMIT 1"));
	}
}