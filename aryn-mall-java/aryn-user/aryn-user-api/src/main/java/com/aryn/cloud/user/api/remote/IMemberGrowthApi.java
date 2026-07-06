package com.aryn.cloud.user.api.remote;

/**
 * 远程会员成长值服务 (Dubbo RPC)
 *
 * @author aryn
 */
public interface IMemberGrowthApi {

	/**
	 * 增加成长值
	 * @param userId 用户ID
	 * @param value 成长值变动(正加负减)
	 * @param source 来源(order/sign_in/review/refund/admin)
	 * @param bizId 业务ID
	 * @return 是否成功
	 */
	boolean addGrowthValue(String userId, Integer value, String source, String bizId);

	/**
	 * 获取用户当前成长值
	 * @param userId 用户ID
	 * @return 当前成长值
	 */
	int getCurrentGrowthValue(String userId);

}