package com.aryn.cloud.user.service;

import java.util.List;

/**
 * 积分过期服务：把到期批次剩余量清零并扣减用户可用积分（写 POINTS_EXPIRE 负流水）。
 */
public interface IPointsExpireService {

	/**
	 * 当前租户内存在「已到期未清零」批次的用户ID列表。
	 */
	List<String> listExpiringUserIds();

	/**
	 * 清零单个用户的到期积分（幂等：批次剩余量已为 0 时直接返回 false）。
	 *
	 * @return 是否产生了过期清零流水
	 */
	boolean expireUser(String userId);

	/**
	 * 清零当前租户全部到期用户。
	 *
	 * @return 产生过期流水的用户数
	 */
	int expireTenant();

}
