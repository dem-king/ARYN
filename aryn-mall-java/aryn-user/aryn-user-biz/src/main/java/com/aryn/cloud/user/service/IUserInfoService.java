
package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.dto.UserAdminUpdateDTO;
import com.aryn.cloud.user.api.dto.UserCreateDTO;
import com.aryn.cloud.user.api.dto.UserPasswordUpdateDTO;
import com.aryn.cloud.user.api.dto.UserProfileUpdateDTO;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.api.vo.UserStatisticsVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:36
 */
public interface IUserInfoService extends IService<UserInfo> {

	/**
	 * 验证手机号是否存在
	 * @param phone 手机号
	 * @author 雨滴kian
	 * @date 2022/3/10 10:07
	 * @return: boolean
	 */
	boolean checkPhone(String phone);

	/**
	 * 分页查询商城用户列表
	 * @param page 分页对象
	 * @param userInfo 查询条件
	 * @author 雨滴kian
	 * @date 2022/7/12
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.UserInfo>
	 */
	IPage<UserRespVO> getPage(Page page, UserInfo userInfo);

	/**
	 * 根据用户ID获取用户信息
	 * @param id 用户ID
	 * @return
	 */
	UserRespVO getUserById(String id);

	/**
	 * 根据手机号和来源类型创建用户
	 * @param phone 手机号
	 * @param clientType 来源类型
	 * @return
	 */
	UserInfo createUser(String phone, String clientType);

	/**
	 * 新增用户
	 * @param userInfo
	 * @return
	 */
	boolean saveUser(UserCreateDTO request, String clientType);

	/**
	 * 修改用户
	 * @param userInfo
	 * @return
	 */
	boolean updateUserById(UserAdminUpdateDTO request);

	boolean updateProfile(String userId, UserProfileUpdateDTO request);

	boolean updatePassword(String userId, UserPasswordUpdateDTO request);

	boolean deleteUser(String userId);

	List<UserStatisticsVO> sourceStatistics(UserInfo userInfo);

	UserInfo createUserByOpenId(String openid, String platformType);

	/**
	 * 调整积分
	 * @param userId 用户ID
	 * @param changeType 变动类型：1-获取；2-消耗
	 * @param changePoint 变动积分
	 * @param remark 备注
	 */
	void adjustPoint(String userId, String changeType, Integer changePoint, String remark);

	/**
	 * 调整余额
	 * @param userId 用户ID
	 * @param changeType 变动类型：1-充值；2-消费；3-调整
	 * @param changeAmount 变动金额
	 * @param remark 备注
	 */
	void adjustBalance(String userId, String changeType, BigDecimal changeAmount, String remark);

	/**
	 * 绑定社交账号
	 * @param userId 用户ID
	 * @param socialAccountId 社交账号ID
	 * @param openId openId
	 */
	void bindSocialAccount(String userId, String socialAccountId, String openId);

	/**
	 * 解绑社交用户
	 * @param socialUserId 社交用户ID
	 */
	void unbindSocialUser(String socialUserId);

	/**
	 * 团队分页（查询邀请人团队）
	 * @param page 分页对象
	 * @param userId 用户ID
	 * @return 分页结果
	 */
	IPage<UserRespVO> getTeamPage(Page page, String userId);

}
