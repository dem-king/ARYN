
package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserInfoVO;

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

	/**
	 * 通过openid查询用户
	 * @param openid openid
	 * @param platformType 来源类型：小程序
	 * @return 用户信息
	 */
	UserInfo getUserByOpenId(String openid, String platformType);

}
