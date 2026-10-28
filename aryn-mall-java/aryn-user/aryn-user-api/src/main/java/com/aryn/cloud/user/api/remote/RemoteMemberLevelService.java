package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;

/**
 * 远程会员等级服务：跨域（促销/订单）会员价资格判定所需的等级快照。
 *
 * <p>所有方法均以入参 tenantId 显式锁定租户上下文查询，同租户内比较；任何异常一律返回 null
 * （fail-closed，不得返回默认等级造成越级白送权益）。
 *
 * @author 雨滴kian
 */
public interface RemoteMemberLevelService {

	/**
	 * 租户内用户当前会员等级；用户无等级/不存在返回 null
	 * @param tenantId 租户ID
	 * @param userId 用户ID
	 * @return 会员等级快照，无等级或查询失败返回 null
	 */
	MemberLevelSnapshotVO getUserLevel(String tenantId, String userId);

	/**
	 * 按等级ID取等级快照；不存在返回 null
	 * @param tenantId 租户ID
	 * @param levelId 会员等级ID
	 * @return 会员等级快照，不存在或查询失败返回 null
	 */
	MemberLevelSnapshotVO getLevelById(String tenantId, String levelId);

}
