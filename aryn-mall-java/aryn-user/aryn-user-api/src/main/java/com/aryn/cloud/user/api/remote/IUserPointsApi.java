package com.aryn.cloud.user.api.remote;

/**
 * 远程用户积分服务 (Dubbo RPC)
 *
 * @author aryn
 */
public interface IUserPointsApi {

	/**
	 * 扣减用户积分
	 * @param userId 用户ID
	 * @param points 扣减积分数（正整数）
	 * @param bizId 业务ID（如兑换记录ID）
	 * @param bizType 业务类型（如 POINTS_EXCHANGE）
	 * @return 是否扣减成功
	 */
	boolean deductPoints(String userId, Integer points, String bizId, String bizType);

	/**
	 * 增加用户积分
	 * @param userId 用户ID
	 * @param points 增加积分数（正整数）
	 * @param bizId 业务ID
	 * @param bizType 业务类型
	 * @return 是否增加成功
	 */
	boolean addPoints(String userId, Integer points, String bizId, String bizType);

}