package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.mapper.DistributionUserMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 分销用户服务实现
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionUserServiceImpl extends ServiceImpl<DistributionUserMapper, DistributionUser>
	implements IDistributionUserService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public DistributionUser register(DistributionUserRegisterDTO dto) {
		// 1. 检查用户是否已存在
		DistributionUser exists = this.getByUserId(dto.getUserId());
		if (exists != null) {
			log.info("分销用户已存在，跳过注册 userId={}", dto.getUserId());
			return exists;
		}

		// 2. 校验邀请人（如果提供了邀请人）
		if (dto.getInviterUserId() != null && !dto.getInviterUserId().isBlank()) {
			// 邀请人不能是自己
			if (dto.getInviterUserId().equals(dto.getUserId())) {
				throw new ArynBusinessException("邀请人不能是自己");
			}
			// 邀请人必须是已存在的启用分销用户
			DistributionUser inviter = this.getByUserId(dto.getInviterUserId());
			if (inviter == null) {
				throw new ArynBusinessException("邀请人不是分销用户");
			}
			if (!MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(inviter.getStatus())) {
				throw new ArynBusinessException("邀请人已被禁用");
			}
		}

		// 3. 创建分销用户
		DistributionUser user = new DistributionUser();
		user.setUserId(dto.getUserId());
		user.setNickname(dto.getNickname());
		user.setAvatar(dto.getAvatar());
		user.setInviterUserId(dto.getInviterUserId());
		user.setTotalCommission(BigDecimal.ZERO);
		user.setAvailableCommission(BigDecimal.ZERO);
		user.setWithdrawnCommission(BigDecimal.ZERO);
		user.setFrozenCommission(BigDecimal.ZERO);
		user.setSubordinateCount(0);
		user.setStatus(MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE);
		this.save(user);

		// 4. 更新邀请人的下级人数
		if (dto.getInviterUserId() != null && !dto.getInviterUserId().isBlank()) {
			DistributionUser inviter = this.getByUserId(dto.getInviterUserId());
			if (inviter != null) {
				int count = inviter.getSubordinateCount() == null ? 1 : inviter.getSubordinateCount() + 1;
				inviter.setSubordinateCount(count);
				this.updateById(inviter);
			}
		}

		log.info("分销用户注册成功 userId={}, inviterUserId={}", dto.getUserId(), dto.getInviterUserId());
		return user;
	}

	@Override
	public Boolean updateStatus(String userId, String status) {
		DistributionUser user = this.getByUserId(userId);
		if (user == null) {
			throw new ArynBusinessException("分销用户不存在");
		}
		user.setStatus(status);
		boolean updated = this.updateById(user);
		log.info("分销用户状态变更 userId={}, status={}", userId, status);
		return updated;
	}

	@Override
	public DistributionUser getByUserId(String userId) {
		return this.getOne(Wrappers.<DistributionUser>lambdaQuery()
			.eq(DistributionUser::getUserId, userId)
			.last("limit 1"));
	}

}
