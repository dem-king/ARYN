
package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;

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

}
