
package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;

import java.time.LocalDateTime;
import java.util.List;

public interface RemoteMallUserService {

	/**
	 * 根据手机号获取用户信息
	 * @param phone 手机号
	 * @param clientType 客户端类型
	 * @return 用户信息
	 */
	UserInfo getInfoByPhone(String phone, String clientType);

	UserInfoVO getUserById(String userId);

	UserInfoVO getUserByPhone(String phone);

	List<UserInfoVO> getUserByIds(List<String> userIds);

	MemberBenefitsVO getMemberBenefits(String userId);

	/**
	 * 通过openid查询用户
	 * @param openid openid
	 * @param platformType 来源类型：小程序
	 * @return 用户信息
	 */
	UserInfo getUserByOpenId(String openid, String platformType);

	/**
	 * 按关键字搜索商城用户（手机号/昵称/用户ID），供配送员绑定商城账号使用
	 * @param keyword 关键字
	 * @param limit 最大返回数量
	 * @return 用户信息列表（不含密码）
	 */
	List<UserInfoVO> searchUsersForBinding(String keyword, int limit);

	/**
	 * 在指定租户内按关键字搜索商城用户（手机号/昵称/用户ID）。
	 *
	 * <p>与 {@link #searchUsersForBinding(String, int)} 的区别：显式按租户精确过滤，
	 * 防止其他租户用户挤占限量名额——共享购物车邀请成员等「同企业选人」场景
	 * 只应看到本租户用户。出参手机号为原文，调用方负责按端脱敏。
	 *
	 * @param tenantId 租户ID（精确过滤；空时返回空列表）
	 * @param keyword 关键字（空时返回空列表）
	 * @param limit 最大返回数量
	 * @return 用户信息列表（不含密码）
	 */
	List<UserInfoVO> searchTenantUsers(String tenantId, String keyword, int limit);

	/**
	 * 查询用户注册时间（新人资格时效判定用；createTime 不随通用 VO 出参）。
	 * @param userId 用户ID
	 * @return 注册时间；用户不存在返回 null
	 */
	LocalDateTime getUserRegisterTime(String userId);

	/**
	 * 统计当前租户商城会员数（租户由调用上下文决定）。
	 */
	Long countMallUsers();

}
