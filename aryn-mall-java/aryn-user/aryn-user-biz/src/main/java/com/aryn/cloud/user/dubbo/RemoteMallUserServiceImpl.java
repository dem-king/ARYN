
package com.aryn.cloud.user.dubbo;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.service.IMemberBenefitService;
import com.aryn.cloud.user.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMallUserServiceImpl implements RemoteMallUserService {

	private final IUserInfoService userInfoService;

	private final IMemberBenefitService memberBenefitService;

	@Override
	public UserInfo getInfoByPhone(String phone, String clientType) {
		UserInfo userInfo = userInfoService.getOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getPhone, phone));
		if (ObjectUtil.isNull(userInfo)) {
			userInfo = userInfoService.createUser(phone, clientType);

		}
		return userInfo;
	}

	@Override
	public UserInfoVO getUserById(String userId) {
		UserInfo userInfo = userInfoService.getById(userId);
		if (Objects.isNull(userInfo)) {
			return null;
		}
		return BeanUtil.copyProperties(userInfo, UserInfoVO.class);
	}

	@Override
	public LocalDateTime getUserRegisterTime(String userId) {
		if (StrUtil.isBlank(userId)) {
			return null;
		}
		// 只取注册时间列，避免把整行（含密码等敏感字段）带出
		UserInfo userInfo = userInfoService.getOne(Wrappers.<UserInfo>lambdaQuery()
			.select(UserInfo::getId, UserInfo::getCreateTime)
			.eq(UserInfo::getId, userId)
			.last("LIMIT 1"));
		return userInfo == null ? null : userInfo.getCreateTime();
	}

	@Override
	public UserInfoVO getUserByPhone(String phone) {
		UserInfo userInfo = userInfoService.getOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getPhone, phone));
		if (Objects.isNull(userInfo)) {
			return null;
		}
		return BeanUtil.copyProperties(userInfo, UserInfoVO.class);
	}

	@Override
	public List<UserInfoVO> getUserByIds(List<String> userIds) {
		List<UserInfo> userInfoList = userInfoService.listByIds(userIds);
		if (!CollectionUtils.isEmpty(userInfoList)) {
			return userInfoList.stream().map(v -> {
				UserInfoVO userInfoVO = new UserInfoVO();
				BeanUtil.copyProperties(v, userInfoVO);
				return userInfoVO;
			}).collect(Collectors.toList());
		}
		return null;
	}

	@Override
	public MemberBenefitsVO getMemberBenefits(String userId) {
		return memberBenefitService.getUserBenefits(userId);
	}

	@Override
	public UserInfo getUserByOpenId(String openid, String platformType) {
		UserInfo userInfo = userInfoService.getOne(Wrappers.<UserInfo>lambdaQuery().eq(UserInfo::getOpenId, openid));
		if (ObjectUtil.isNull(userInfo)) {
			userInfo = userInfoService.createUserByOpenId(openid, platformType);
		}
		return userInfo;
	}

	@Override
	public List<UserInfoVO> searchUsersForBinding(String keyword, int limit) {
		int safeLimit = Math.min(Math.max(limit, 1), 50);
		List<UserInfo> users = userInfoService.list(Wrappers.<UserInfo>lambdaQuery()
			.and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
				.like(UserInfo::getPhone, keyword)
				.or()
				.like(UserInfo::getNickname, keyword)
				.or()
				.eq(UserInfo::getId, keyword))
			.last("LIMIT " + safeLimit));
		return users.stream().map(v -> {
			UserInfoVO vo = new UserInfoVO();
			BeanUtil.copyProperties(v, vo);
			vo.setPassword(null);
			return vo;
		}).collect(Collectors.toList());
	}

}
