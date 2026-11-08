package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;

import java.util.List;

/**
 * SPU 社证统计远程接口：订单域 T+1 快照供商品域卡面「月N+人已下单 / 回购率」消费。
 *
 * <p>口径与热销榜（{@link RemoteGoodsSalesRankService}）同源，只是聚合维度不同：
 * 热销榜求成交件数排行，本接口求买家数/复购人数。实现侧保证不抛异常，
 * 任何失败由消费方降级为空标签。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface RemoteGoodsSalesStatService {

	/**
	 * 查询各 SPU 最新一期的社证统计（每 SPU 取 stat_date 最大行；stat_date 晚于今日的行不下发）。
	 *
	 * @param tenantId 租户 ID（缺失时 fail-closed 返回空列表）
	 * @param spuIds   SPU ID 集合，超出上限部分忽略（社证缺失只影响卡面文案，不影响交易）
	 * @return 命中的统计条目；无数据返回空列表
	 */
	List<GoodsSpuSalesStatDTO> latestStats(String tenantId, List<String> spuIds);

}
