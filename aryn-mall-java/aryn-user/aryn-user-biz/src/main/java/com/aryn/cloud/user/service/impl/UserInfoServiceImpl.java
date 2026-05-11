/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.desensitization.MobilePhoneDesensitization;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.api.vo.UserStatisticsVO;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IBalanceRecordService;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

	private final MobilePhoneDesensitization mobilePhoneDesensitization = new MobilePhoneDesensitization();

	private final IPointsRecordService pointsRecordService;

	private final IBalanceRecordService balanceRecordService;

	private final SocialUserMapper socialUserMapper;

	@Override
	public IPage<UserRespVO> getPage(Page page, UserInfo userInfo) {
		return baseMapper.selectAdminPage(page, userInfo);
	}

	@Override
	public boolean checkPhone(String phone) {
		if (StrUtil.isBlank(phone)) {
			throw new ArynBusinessException("手机号为空");
		}
		return baseMapper.selectCount(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getPhone, phone)) > 0;
	}

	@Override
	public UserRespVO getUserById(String id) {
		UserInfo userInfo = this.getById(id);
		if (Objects.isNull(userInfo)) {
			return null;
		}
		UserRespVO userRespVO = new UserRespVO();
		BeanUtils.copyProperties(userInfo, userRespVO);
		return userRespVO;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public UserInfo createUser(String phone, String clientType) {
		UserInfo userInfo = new UserInfo();
		userInfo.setPhone(phone);
		userInfo.setUserSource(clientType);
		userInfo.setNickname(mobilePhoneDesensitization.serialize(userInfo.getPhone()));
		userInfo.setCreateBy(phone);
		this.save(userInfo);
		return userInfo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveUser(UserInfo userInfo) {
		if (this.checkPhone(userInfo.getPhone())) {
			throw new RuntimeException("手机号已存在");
		}
		if (StringUtils.hasText(userInfo.getPassword())) {
			userInfo.setPassword(BCrypt.hashpw(userInfo.getPassword()));
		}
		return this.save(userInfo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateUserById(UserInfo userInfo) {
		UserInfo target = this.getById(userInfo.getId());
		userInfo.setPhone(null);
		userInfo.setPassword(null);
		BeanUtils.copyProperties(userInfo, target);

		this.updateById(target);

		return true;
	}

	@Override
	public List<UserStatisticsVO> sourceStatistics(UserInfo userInfo) {
		return baseMapper.sourceStatistics(userInfo);
	}

	@Override
	public UserInfo createUserByOpenId(String openid, String platformType) {
		UserInfo userInfo = new UserInfo();
		userInfo.setOpenId(openid);
		userInfo.setUserSource(platformType);
		userInfo.setNickname("微信用户" + RandomUtil.randomNumbers(4));
		userInfo.setCreateBy(openid);
		this.save(userInfo);
		return userInfo;
	}

	@Override
	public void adjustPoint(String userId, String changeType, Integer changePoint, String remark) {
		pointsRecordService.recordPointsChange(userId, changeType, changePoint, "ADMIN_ADJUST", remark);
	}

	@Override
	public void adjustBalance(String userId, String changeType, BigDecimal changeAmount, String remark) {
		balanceRecordService.recordBalanceChange(userId, changeType, changeAmount, "ADMIN_ADJUST", remark);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindSocialAccount(String userId, String socialAccountId, String openId) {
		SocialUser socialUser = new SocialUser();
		socialUser.setSocialAccountId(socialAccountId);
		socialUser.setOpenId(openId);
		socialUser.setMallUserId(userId);
		socialUserMapper.insert(socialUser);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unbindSocialUser(String socialUserId) {
		socialUserMapper.deleteById(socialUserId);
	}

	@Override
	public IPage<UserRespVO> getTeamPage(Page page, String userId) {
		// 查询邀请人团队：查找openId等于该userId的所有用户
		UserInfo query = new UserInfo();
		query.setOpenId(userId);
		return baseMapper.selectAdminPage(page, query);
	}

}
