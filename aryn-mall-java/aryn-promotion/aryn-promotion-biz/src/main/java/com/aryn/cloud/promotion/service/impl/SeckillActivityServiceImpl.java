package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.SeckillActivityDTO;
import com.aryn.cloud.promotion.api.dto.SeckillGoodsDTO;
import com.aryn.cloud.promotion.api.dto.SeckillSessionDTO;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import com.aryn.cloud.promotion.api.vo.AppSeckillVO;
import com.aryn.cloud.promotion.api.vo.SeckillActivityVO;
import com.aryn.cloud.promotion.api.vo.SeckillGoodsVO;
import com.aryn.cloud.promotion.api.vo.SeckillSessionVO;
import com.aryn.cloud.promotion.mapper.SeckillActivityMapper;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.promotion.service.SeckillStockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity>
		implements ISeckillActivityService {

	private final ISeckillSessionService seckillSessionService;

	private final ISeckillGoodsService seckillGoodsService;

	private final SeckillStockManager seckillStockManager;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@Override
	public IPage<SeckillActivity> getAdminPage(Page page, SeckillActivity activity) {
		return baseMapper.selectAdminPage(page, activity);
	}

	@Override
	public SeckillActivityVO getDetail(String id) {
		SeckillActivity activity = this.getById(id);
		if (activity == null) {
			return null;
		}
		SeckillActivityVO vo = new SeckillActivityVO();
		BeanUtils.copyProperties(activity, vo);
		// 查询场次
		List<SeckillSession> sessions = seckillSessionService.listByActivityId(id);
		List<SeckillSessionVO> sessionVOs = sessions.stream().map(session -> {
			SeckillSessionVO sessionVO = new SeckillSessionVO();
			BeanUtils.copyProperties(session, sessionVO);
			// 查询场次商品
			List<SeckillGoods> goodsList = seckillGoodsService.listBySessionId(session.getId());
			List<SeckillGoodsVO> goodsVOs = goodsList.stream().map(goods -> {
				SeckillGoodsVO goodsVO = new SeckillGoodsVO();
				BeanUtils.copyProperties(goods, goodsVO);
				goodsVO.setRemainingStock(goods.getSeckillStock() - Optional.ofNullable(goods.getSoldCount()).orElse(0));
				return goodsVO;
			}).collect(Collectors.toList());
			sessionVO.setGoodsList(goodsVOs);
			return sessionVO;
		}).collect(Collectors.toList());
		vo.setSessions(sessionVOs);
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveWithSessions(SeckillActivityDTO dto) {
		// 保存活动
		SeckillActivity activity = new SeckillActivity();
		BeanUtils.copyProperties(dto, activity);
		activity.setStatus(0);
		boolean saved = this.save(activity);
		if (!saved) {
			throw new ArynBusinessException("保存秒杀活动失败");
		}
		String activityId = activity.getId();
		// 保存场次和商品
		saveSessionsAndGoods(activityId, dto.getSessions(), dto.getEndTime());
		log.info("秒杀活动创建成功, activityId={}, activityName={}", activityId, dto.getActivityName());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateWithSessions(SeckillActivityDTO dto) {
		if (dto.getId() == null) {
			throw new ArynBusinessException("活动ID不能为空");
		}
		SeckillActivity activity = this.getById(dto.getId());
		if (activity == null) {
			throw new ArynBusinessException("秒杀活动不存在");
		}
		// 进行中的活动禁止修改场次和库存（防止数据丢失和库存不一致）
		if (activity.getStatus() != null && activity.getStatus() == 1
				&& dto.getSessions() != null && !dto.getSessions().isEmpty()) {
			throw new ArynBusinessException("进行中的活动不能修改场次和库存，请先暂停");
		}
		BeanUtils.copyProperties(dto, activity);
		boolean updated = this.updateById(activity);
		if (!updated) {
			throw new ArynBusinessException("更新秒杀活动失败");
		}
		// 仅当传入了新场次数据时才重建（进行中活动不允许，已在上面拦截）
		if (dto.getSessions() != null && !dto.getSessions().isEmpty()) {
			// 删除原有场次和商品（逻辑删除），并清理旧 Redis 库存
			List<SeckillSession> oldSessions = seckillSessionService.listByActivityId(activity.getId());
			for (SeckillSession session : oldSessions) {
				List<SeckillGoods> oldGoods = seckillGoodsService.listBySessionId(session.getId());
				for (SeckillGoods goods : oldGoods) {
					seckillStockManager.removeStock(activity.getId(), session.getId(), goods.getSkuId());
				}
				seckillGoodsService.remove(Wrappers.<SeckillGoods>lambdaQuery()
						.eq(SeckillGoods::getSessionId, session.getId()));
				seckillSessionService.removeById(session.getId());
			}
			// 重新保存场次和商品
			saveSessionsAndGoods(activity.getId(), dto.getSessions(), dto.getEndTime());
		}
		log.info("秒杀活动更新成功, activityId={}", dto.getId());
		return true;
	}

	@Override
	public boolean updateStatus(String id, Integer status) {
		return this.update(Wrappers.<SeckillActivity>lambdaUpdate()
				.eq(SeckillActivity::getId, id)
				.set(SeckillActivity::getStatus, status));
	}

	@Override
	public List<AppSeckillVO> getActiveSessions() {
		LocalDateTime now = LocalDateTime.now();
		// 查询进行中的场次
		List<SeckillSession> sessions = seckillSessionService.list(Wrappers.<SeckillSession>lambdaQuery()
				.eq(SeckillSession::getStatus, 1)
				.le(SeckillSession::getStartTime, now)
				.gt(SeckillSession::getEndTime, now)
				.orderByAsc(SeckillSession::getStartTime));
		return sessions.stream().map(this::convertToAppSeckillVO).collect(Collectors.toList());
	}

	@Override
	public AppSeckillVO getSessionGoods(String sessionId) {
		SeckillSession session = seckillSessionService.getById(sessionId);
		if (session == null) {
			return null;
		}
		return convertToAppSeckillVO(session);
	}

	@Override
	public AppSeckillGoodsVO getGoodsSeckillInfo(String skuId) {
		SeckillGoods goods = seckillGoodsService.getBySkuId(skuId);
		if (goods == null) {
			return null;
		}
		AppSeckillGoodsVO vo = new AppSeckillGoodsVO();
		BeanUtils.copyProperties(goods, vo);
		// 从 Redis 获取实时库存
		Integer remaining = seckillStockManager.getRemainingStock(
				goods.getActivityId(), goods.getSessionId(), goods.getSkuId());
		vo.setRemainingStock(remaining != null ? remaining : goods.getSeckillStock() - Optional.ofNullable(goods.getSoldCount()).orElse(0));
		// 查询商品信息
		List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(List.of(skuId));
		if (!CollectionUtils.isEmpty(skus)) {
			GoodsSku sku = skus.get(0);
			vo.setGoodsImage(sku.getPicUrl());
			vo.setOriginalPrice(sku.getSalesPrice());
		}
		return vo;
	}

	// ==================== 私有方法 ====================

	private void saveSessionsAndGoods(String activityId, List<SeckillSessionDTO> sessions,
			LocalDateTime activityEndTime) {
		if (CollectionUtils.isEmpty(sessions)) {
			return;
		}
		Duration ttl = Duration.between(LocalDateTime.now(), activityEndTime).plusHours(1);
		// TTL 不能为负，否则 Redis set 会失败
		if (ttl.isNegative()) {
			ttl = Duration.ofHours(1);
		}
		// 收集需要在事务提交后初始化的 Redis 库存任务
		List<StockInitTask> stockInitTasks = new ArrayList<>();
		for (SeckillSessionDTO sessionDTO : sessions) {
			SeckillSession session = new SeckillSession();
			BeanUtils.copyProperties(sessionDTO, session);
			session.setActivityId(activityId);
			session.setStatus(0);
			seckillSessionService.save(session);
			// 保存商品
			if (!CollectionUtils.isEmpty(sessionDTO.getGoodsList())) {
				List<SeckillGoods> goodsList = new ArrayList<>();
				for (SeckillGoodsDTO goodsDTO : sessionDTO.getGoodsList()) {
					SeckillGoods goods = new SeckillGoods();
					BeanUtils.copyProperties(goodsDTO, goods);
					goods.setActivityId(activityId);
					goods.setSessionId(session.getId());
					goods.setSoldCount(0);
					if (goods.getLimitPerUser() == null) {
						goods.setLimitPerUser(1);
					}
					goodsList.add(goods);
				}
				seckillGoodsService.saveBatch(goodsList);
				// 收集 Redis 库存初始化任务（延迟到事务提交后执行）
				for (SeckillGoods goods : goodsList) {
					stockInitTasks.add(new StockInitTask(
							activityId, session.getId(), goods.getSkuId(),
							goods.getSeckillStock(), goods.getLimitPerUser(), ttl));
				}
			}
		}
		// 将 Redis 库存初始化移到事务提交后，确保 DB 事务成功后再写 Redis
		if (!stockInitTasks.isEmpty()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					for (StockInitTask task : stockInitTasks) {
						try {
							seckillStockManager.initStock(
									task.activityId(), task.sessionId(), task.skuId(),
									task.stock(), task.limitPerUser(), task.ttl());
						} catch (Exception e) {
							log.error("Redis 库存初始化失败, activityId={}, sessionId={}, skuId={}",
									task.activityId(), task.sessionId(), task.skuId(), e);
						}
					}
				}
			});
		}
	}

	/**
	 * Redis 库存初始化任务参数
	 */
	private record StockInitTask(String activityId, String sessionId, String skuId,
			Integer stock, Integer limitPerUser, Duration ttl) {
	}

	private AppSeckillVO convertToAppSeckillVO(SeckillSession session) {
		AppSeckillVO vo = new AppSeckillVO();
		vo.setSessionId(session.getId());
		vo.setActivityId(session.getActivityId());
		vo.setSessionName(session.getSessionName());
		vo.setStartTime(session.getStartTime());
		vo.setEndTime(session.getEndTime());
		vo.setStatus(session.getStatus());
		// 倒计时（不能为负数）
		LocalDateTime now = LocalDateTime.now();
		if (session.getStatus() == 0) {
			vo.setCountdown(Math.max(0, Duration.between(now, session.getStartTime()).getSeconds()));
		} else if (session.getStatus() == 1) {
			vo.setCountdown(Math.max(0, Duration.between(now, session.getEndTime()).getSeconds()));
		} else {
			vo.setCountdown(0L);
		}
		// 查询商品列表
		List<SeckillGoods> goodsList = seckillGoodsService.listBySessionId(session.getId());
		if (CollectionUtils.isEmpty(goodsList)) {
			vo.setGoodsList(List.of());
			return vo;
		}
		// 批量查询 SKU 信息
		List<String> skuIds = goodsList.stream().map(SeckillGoods::getSkuId).distinct().toList();
		List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(skuIds);
		Map<String, GoodsSku> skuMap = skus.stream().collect(Collectors.toMap(GoodsSku::getId, s -> s, (a, b) -> a));
		// 批量获取 Redis 剩余库存（mget 优化，避免 N 次 Redis 调用）
		List<String[]> stockKeysParams = goodsList.stream()
				.map(g -> new String[]{g.getActivityId(), g.getSessionId(), g.getSkuId()})
				.toList();
		List<Integer> remainingStocks = seckillStockManager.batchGetRemainingStock(stockKeysParams);
		List<AppSeckillGoodsVO> goodsVOs = new ArrayList<>();
		for (int i = 0; i < goodsList.size(); i++) {
			SeckillGoods goods = goodsList.get(i);
			AppSeckillGoodsVO goodsVO = new AppSeckillGoodsVO();
			BeanUtils.copyProperties(goods, goodsVO);
			GoodsSku sku = skuMap.get(goods.getSkuId());
			if (sku != null) {
				goodsVO.setGoodsImage(sku.getPicUrl());
				goodsVO.setOriginalPrice(sku.getSalesPrice());
			}
			Integer remaining = remainingStocks.get(i);
			goodsVO.setRemainingStock(remaining != null ? remaining : goods.getSeckillStock() - Optional.ofNullable(goods.getSoldCount()).orElse(0));
			goodsVOs.add(goodsVO);
		}
		vo.setGoodsList(goodsVOs);
		return vo;
	}
}