package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;

/**
 * 积分统一发放/抵扣服务：所有场景先经过积分规则配置，再写入积分账本。
 */
public interface IPointsGrantService {

	/**
	 * 按场景发放积分。
	 * @param userId 用户ID
	 * @param scene 触发场景
	 * @param bizNo 业务单号（幂等键）
	 * @param overridePoints 指定积分值；为空时读取配置的固定积分值。
	 *        ORDER_COMPLETE 按金额动态计算时传入该值，但配置关闭时仍不发放
	 * @param remark 备注
	 * @return 实际发放积分；规则未启用或无需发放返回 0
	 */
	int grant(String userId, PointsSceneEnum scene, String bizNo, Integer overridePoints, String remark);

	/**
	 * 订单完成积分倍率配置：每 1 元商品实付金额获得的基础积分。
	 * 配置不存在时回退为 1（兼容未执行增量脚本的存量库）；配置被禁用时返回 0。
	 */
	int getOrderCompleteRate();

	/**
	 * 查询积分抵现上下文。
	 */
	PointsDeductContextVO getDeductContext(String userId);

	/**
	 * 扣减积分（业务单号幂等）。
	 */
	void deduct(String userId, Integer points, String bizNo, String remark);

	/**
	 * 按场景扣减积分（业务单号幂等）：积分兑换等非订单消耗场景使用，
	 * 流水 triggerScene 记录为传入场景便于 C 端/管理端按场景筛选。
	 */
	void consume(String userId, Integer points, PointsSceneEnum scene, String bizNo, String remark);

	/**
	 * 返还积分（业务单号幂等）。
	 */
	void refund(String userId, Integer points, String bizNo, String remark);

}
