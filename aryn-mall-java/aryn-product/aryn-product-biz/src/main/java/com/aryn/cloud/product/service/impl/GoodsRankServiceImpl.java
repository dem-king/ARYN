package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesRankService;
import com.aryn.cloud.product.api.vo.AppGoodsRankVO;
import com.aryn.cloud.product.mapper.GoodsRankMapper;
import com.aryn.cloud.product.service.IGoodsRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C 端商品榜单实现（搜索页「热门榜单」）。
 *
 * <p>数据只来自真实行为，两个榜单都不使用 {@code goods_spu.sales_volume}（该列是种子数据）：
 * 热搜榜按当前租户近 30 天 {@code goods_footprint} 浏览足迹聚合；
 * 热销榜通过订单域 {@link RemoteGoodsSalesRankService} 按近 90 天有效订单聚合，
 * 再回商品域过滤为当前在售商品并保持订单域名次。
 *
 * <p>两条链路都 fail-closed：租户缺失直接返回空列表；远程异常 catch 后打 warn 降级为空列表，
 * 榜单是搜索页的增强内容，任何失败都不能打断搜索主流程。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsRankServiceImpl implements IGoodsRankService {

	/** 热搜榜统计窗口：近 30 天浏览足迹 */
	private static final int HOT_WINDOW_DAYS = 30;

	/** 热销榜统计窗口：近 90 天有效订单（与常购清单口径一致） */
	private static final int SALES_WINDOW_DAYS = 90;

	/** 榜单展示条数（服务端取 10 条，商品域过滤下架后可能不足 10 条） */
	private static final int RANK_LIMIT = 10;

	private final GoodsRankMapper goodsRankMapper;

	/**
	 * 订单域热销榜远程接口。
	 *
	 * <p>非 final 字段 + {@code @DubboReference} 注入：构造器注入远程接口曾在 boot 启动失败
	 * （参照 NewbieActivityServiceImpl / PromotionEngineServiceImpl 的既有结论）。
	 */
	@DubboReference
	private RemoteGoodsSalesRankService remoteGoodsSalesRankService;

	@Override
	public List<AppGoodsRankVO> hotTop10(String tenantId) {
		// fail-closed：租户缺失宁可空榜，也绝不能退化成跨租户浏览数据
		if (!StringUtils.hasText(tenantId)) {
			return Collections.emptyList();
		}
		List<AppGoodsRankVO> ranks = goodsRankMapper.selectHotTop10(tenantId, HOT_WINDOW_DAYS, RANK_LIMIT);
		return ranks == null ? Collections.emptyList() : ranks;
	}

	@Override
	public List<AppGoodsRankVO> salesTop10(String tenantId) {
		if (!StringUtils.hasText(tenantId)) {
			return Collections.emptyList();
		}
		List<GoodsSalesRankDTO> salesRank;
		try {
			salesRank = remoteGoodsSalesRankService.topSalesRank(tenantId, SALES_WINDOW_DAYS, RANK_LIMIT);
		}
		catch (Exception ex) {
			// 跨域取数失败不影响搜索主流程：降级空榜，前端展示空态
			log.warn("热销榜远程查询异常，降级为空榜单 tenant={}", tenantId, ex);
			return Collections.emptyList();
		}
		if (salesRank == null || salesRank.isEmpty()) {
			return Collections.emptyList();
		}

		// 订单域名次是唯一排序依据：先收集 spuId 顺序（去重且保序），再批量补当前在售商品
		Set<String> spuIds = new LinkedHashSet<>();
		for (GoodsSalesRankDTO rank : salesRank) {
			if (rank != null && StringUtils.hasText(rank.getSpuId())) {
				spuIds.add(rank.getSpuId());
			}
		}
		if (spuIds.isEmpty()) {
			return Collections.emptyList();
		}

		List<AppGoodsRankVO> onSaleGoods = goodsRankMapper.selectOnSaleByIds(tenantId, new ArrayList<>(spuIds));
		Map<String, AppGoodsRankVO> onSaleById = new HashMap<>();
		if (onSaleGoods != null) {
			onSaleGoods.forEach(vo -> onSaleById.put(vo.getId(), vo));
		}

		List<AppGoodsRankVO> result = new ArrayList<>(spuIds.size());
		for (String spuId : spuIds) {
			// 下架/删除/跨租户商品跳过，名次顺延，保证榜单只出现当前可买商品
			AppGoodsRankVO current = onSaleById.get(spuId);
			if (current != null) {
				result.add(current);
			}
		}
		return result;
	}

}
