package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.dto.GoodsSalesRankDTO;

import java.util.List;

/**
 * 商品热销榜远程接口：订单域按真实有效订单聚合 SPU 累计成交件数，供商品域榜单消费。
 *
 * <p>热销口径与「常购清单」（{@code OrderItemMapper#selectFrequentPurchase}）一致：
 * 近 N 天 {@code order_info.status IN ('2','3','4')}（待发货/待收货/已完成，含货到付款）
 * 且订单、明细均未删除的 {@code order_item.buy_quantity} 求和，按累计件数倒序。
 * 商品域拿到名次后需自行过滤为当前在售商品并保持名次。
 *
 * @author aryn
 * @since 2026/10/25
 */
public interface RemoteGoodsSalesRankService {

	/**
	 * 查询租户热销榜。
	 *
	 * @param tenantId 租户 ID（缺失时 fail-closed 返回空列表）
	 * @param days 统计窗口天数（1~3650，越界钳制）
	 * @param limit 返回条数（1~50，越界钳制）
	 * @return 按累计成交件数倒序的榜单条目；无数据返回空列表
	 */
	List<GoodsSalesRankDTO> topSalesRank(String tenantId, int days, int limit);

}
