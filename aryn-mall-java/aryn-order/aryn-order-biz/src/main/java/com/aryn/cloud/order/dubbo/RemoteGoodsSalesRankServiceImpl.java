package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesRankService;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 商品热销榜远程实现：按租户聚合近 N 天真实有效订单的 SPU 累计成交件数。
 *
 * <p>热销口径与「常购清单」（{@link OrderItemMapper#selectFrequentPurchase}）保持一致：
 * {@code order_info.status IN ('2','3','4')}（已过支付环节，含货到付款）、
 * 订单与明细均未删除、同租户，近 N 天按 {@code order_info.create_time} 过滤，
 * 明细按 spu_id 汇总 {@code buy_quantity} 后倒序取前 limit 条。
 *
 * <p>商品域消费方只拿名次与快照字段，下架/删除商品由商品域回收，本服务不做商品状态判断
 * （订单域不持有商品在售状态，跨库判断会把商品域语义带进订单域）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsSalesRankServiceImpl implements RemoteGoodsSalesRankService {

	/** 统计窗口下限（天） */
	private static final int MIN_DAYS = 1;

	/** 统计窗口上限（天，约 10 年，防止异常入参把 SQL 拖成全表扫描） */
	private static final int MAX_DAYS = 3650;

	/** 榜单条数下限 */
	private static final int MIN_LIMIT = 1;

	/** 榜单条数上限（C 端榜单最多取 10，留出商品域过滤下架商品的余量） */
	private static final int MAX_LIMIT = 50;

	private final OrderItemMapper orderItemMapper;

	@Override
	public List<GoodsSalesRankDTO> topSalesRank(String tenantId, int days, int limit) {
		// fail-closed：租户缺失直接返回空榜，绝不退化成跨租户聚合
		if (!StringUtils.hasText(tenantId)) {
			return Collections.emptyList();
		}
		int safeDays = clamp(days, MIN_DAYS, MAX_DAYS);
		int safeLimit = clamp(limit, MIN_LIMIT, MAX_LIMIT);
		List<GoodsSalesRankDTO> ranks = orderItemMapper.selectGoodsSalesRank(tenantId, safeDays, safeLimit);
		return ranks == null ? Collections.emptyList() : ranks;
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

}
