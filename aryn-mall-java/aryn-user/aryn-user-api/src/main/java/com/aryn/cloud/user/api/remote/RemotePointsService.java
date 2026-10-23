package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.vo.PointsDeductContextVO;

/**
 * 积分远程服务：订单域结算、下单、取消与退款使用。
 */
public interface RemotePointsService {

	/**
	 * 查询积分抵现上下文（是否启用、可用积分、每元所需积分）。
	 * @param userId 用户ID
	 * @return 抵扣上下文；用户不存在时可用积分为 0
	 */
	PointsDeductContextVO getDeductContext(String userId);

	/**
	 * 扣减积分。业务单号幂等：同一 bizNo 重复调用不会重复扣减。
	 * @param userId 用户ID
	 * @param points 扣减积分（正整数）
	 * @param bizNo 业务单号（订单域使用 ORDER_DEDUCT:订单号）
	 * @param remark 备注
	 */
	void deductPoints(String userId, Integer points, String bizNo, String remark);

	/**
	 * 返还积分。业务单号幂等：同一 bizNo 重复调用不会重复返还。
	 * @param userId 用户ID
	 * @param points 返还积分（正整数）
	 * @param bizNo 业务单号（订单域使用 ORDER_REFUND:订单号）
	 * @param remark 备注
	 */
	void refundPoints(String userId, Integer points, String bizNo, String remark);

}
