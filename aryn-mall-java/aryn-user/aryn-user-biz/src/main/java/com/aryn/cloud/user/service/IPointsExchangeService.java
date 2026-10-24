package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.entity.PointsExchange;
import com.aryn.cloud.user.api.entity.PointsExchangeRecord;
import com.aryn.cloud.user.api.vo.PointsExchangeVO;

import java.util.List;

/**
 * 积分兑换：积分兑换优惠券（消耗侧「兑券」形态，补贴比直接抵现更可控）。
 */
public interface IPointsExchangeService {

	/**
	 * C 端可兑列表：仅启用项，附券模板展示信息与本人已兑次数。
	 */
	List<PointsExchangeVO> listApplicable(String userId);

	/**
	 * 兑换：校验上限/余额 → 落记录 → 扣积分（幂等键 POINTS_EXCHANGE:{记录ID}）→ 发券
	 * （sourceType=POINTS_EXCHANGE，sourceId=记录ID）。任一步失败整体回滚。
	 */
	PointsExchangeRecord exchange(String userId, String exchangeId);

	/**
	 * 管理端全量列表（含下架项）。
	 */
	List<PointsExchange> listAll();

	/**
	 * 管理端保存（新增/编辑）。
	 */
	boolean saveExchange(PointsExchange exchange);

	/**
	 * 管理端删除（逻辑删除）。
	 */
	boolean removeExchange(String id);

}
