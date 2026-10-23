package com.aryn.cloud.order.api.remote;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车远程接口：供其他 biz 模块（如 vessel）驱动购物车归属数据的维护。
 *
 * @author aryn
 * @since 2026/10/4
 */
public interface RemoteShoppingCartService {

	/**
	 * 船舶获得可用靠港时，把该船「无靠港归属或挂在已结束靠港」的购物车行迁移到新靠港。
	 *
	 * <p>背景：购物车行在加购时刻快照船舶与靠港归属（防串船分组键），「有船无靠港」
	 * 期间加购的行 vessel_call_id 落空，靠港结束后新加购的行同样只带船不带靠港；
	 * 等新靠港出现后，用户期望这批商品顺延到新航次而非删掉重加。下单时订单的船舶
	 * 参数本就取自用户当前上下文，迁移只是让落库快照与系统实际行为对齐。
	 *
	 * <p>租户与船舶参数全部显式传入：远程调用链路上的租户上下文不可依赖
	 * （boot 模式 Dubbo injvm 的提供方过滤器会清掉调用方线程上下文）。
	 *
	 * @param tenantId 租户ID
	 * @param vesselId 船舶ID
	 * @param vesselCallId 新靠港ID（必须是该船当前可用的靠港）
	 * @param staleCallIds 该船已结束/已取消的靠港ID，挂在这些靠港上的行一并顺延（可为空）
	 * @return 迁移的行数
	 */
	int reattachRowsToVesselCall(String tenantId, String vesselId, String vesselCallId, List<String> staleCallIds);

	/**
	 * 统计一批 SKU 在购物车中的去重人数，供秒杀会场展示「N 人在抢」。
	 *
	 * <p>口径：租户内、未删除、加购时间不早于 {@code since} 的**去重 user_id 数**。
	 * 不是加购件数、也不是下单数 —— 购物车里当前还放着的才算"在抢"，买完行被清掉后自然减一。
	 * 只读统计，不参与下单/库存链路；调用方需容忍失败（降级为不展示）。
	 *
	 * @param tenantId 租户ID（显式传入：Dubbo 调用链路不依赖调用方线程上下文）
	 * @param skuIds 秒杀场次内的 SKU 集合（空集合直接返回 0，不发起 SQL）
	 * @param since 统计起点，通常取场次开始时间
	 * @return 去重人数
	 */
	int countDistinctSkuUsersSince(String tenantId, List<String> skuIds, LocalDateTime since);

}
