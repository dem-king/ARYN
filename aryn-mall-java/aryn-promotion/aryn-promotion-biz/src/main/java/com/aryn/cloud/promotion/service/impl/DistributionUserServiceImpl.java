package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.enums.DistributionWithdrawStatusEnum;
import com.aryn.cloud.promotion.mapper.DistributionUserMapper;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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

	private final DistributionWithdrawMapper distributionWithdrawMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public DistributionUser register(DistributionUserRegisterDTO dto) {
		DistributionUser exists = this.getByUserId(dto.getUserId());
		if (exists != null) {
			bindInviterIfAbsent(exists, dto.getInviterUserId());
			log.info("分销用户已存在，完成幂等注册 userId={}", dto.getUserId());
			return exists;
		}

		DistributionUser inviter = validateInviter(dto.getUserId(), dto.getInviterUserId());

		DistributionUser user = new DistributionUser();
		user.setUserId(dto.getUserId());
		user.setNickname(dto.getNickname());
		user.setAvatar(dto.getAvatar());
		user.setInviterUserId(dto.getInviterUserId());
		user.setTotalCommission(BigDecimal.ZERO);
		user.setAvailableCommission(BigDecimal.ZERO);
		user.setPendingCommission(BigDecimal.ZERO);
		user.setWithdrawnCommission(BigDecimal.ZERO);
		user.setFrozenCommission(BigDecimal.ZERO);
		user.setCommissionDebt(BigDecimal.ZERO);
		user.setSubordinateCount(0);
		user.setStatus(MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE);
		if (!this.save(user)) {
			throw new ArynBusinessException("分销用户注册失败，请重试");
		}

		if (inviter != null) {
			incrementSubordinate(inviter);
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

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean removeSafely(String id) {
		DistributionUser user = baseMapper.selectByIdForUpdate(id);
		if (user == null) {
			throw new ArynBusinessException("分销用户不存在");
		}
		if (user.getSubordinateCount() != null && user.getSubordinateCount() > 0) {
			throw new ArynBusinessException("分销用户仍有下级，禁止删除");
		}
		Long activeChildCount = baseMapper.selectCount(Wrappers.<DistributionUser>lambdaQuery()
			.eq(DistributionUser::getInviterUserId, user.getUserId()));
		if (activeChildCount != null && activeChildCount > 0) {
			throw new ArynBusinessException("分销用户仍有有效下级，禁止删除");
		}
		if (hasUnsettledAmount(user)) {
			throw new ArynBusinessException("分销用户仍有未清资金，禁止删除");
		}
		Long pendingWithdrawCount = distributionWithdrawMapper.selectCount(
			Wrappers.<DistributionWithdraw>lambdaQuery()
				.eq(DistributionWithdraw::getUserId, user.getUserId())
				.eq(DistributionWithdraw::getStatus, DistributionWithdrawStatusEnum.STATUS_0.getCode()));
		if (pendingWithdrawCount != null && pendingWithdrawCount > 0) {
			throw new ArynBusinessException("分销用户仍有待审核提现，禁止删除");
		}
		Long refundableOrderCount = baseMapper.countRefundableOrders(user.getUserId());
		if (refundableOrderCount != null && refundableOrderCount > 0) {
			throw new ArynBusinessException("分销用户仍有可退款佣金订单，禁止删除");
		}
		if (!this.removeById(id)) {
			throw new ArynBusinessException("分销用户删除失败，请重试");
		}
		decrementInviterSubordinateCount(user.getInviterUserId());
		return Boolean.TRUE;
	}

	private void bindInviterIfAbsent(DistributionUser user, String inviterUserId) {
		if (user.getInviterUserId() != null && !user.getInviterUserId().isBlank()) {
			return;
		}
		DistributionUser inviter = validateInviter(user.getUserId(), inviterUserId);
		if (inviter == null) {
			return;
		}
		user.setInviterUserId(inviterUserId);
		if (!this.updateById(user)) {
			throw new ArynBusinessException("邀请关系绑定失败，请重试");
		}
		incrementSubordinate(inviter);
	}

	private DistributionUser validateInviter(String userId, String inviterUserId) {
		if (inviterUserId == null || inviterUserId.isBlank()) {
			return null;
		}
		if (inviterUserId.equals(userId)) {
			throw new ArynBusinessException("邀请人不能是自己");
		}
		DistributionUser inviter = baseMapper.selectByUserIdForUpdate(inviterUserId);
		if (inviter == null) {
			throw new ArynBusinessException("邀请人不是分销用户");
		}
		if (!MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(inviter.getStatus())) {
			throw new ArynBusinessException("邀请人已被禁用");
		}
		validateNoInviteCycle(userId, inviter);
		return inviter;
	}

	private void validateNoInviteCycle(String userId, DistributionUser inviter) {
		Set<String> visited = new HashSet<>();
		DistributionUser current = inviter;
		while (current != null) {
			if (userId.equals(current.getUserId())) {
				throw new ArynBusinessException("邀请关系不能形成循环");
			}
			if (!visited.add(current.getUserId())) {
				throw new ArynBusinessException("邀请关系中已存在循环");
			}
			String parentUserId = current.getInviterUserId();
			if (parentUserId == null || parentUserId.isBlank()) {
				return;
			}
			current = this.getByUserId(parentUserId);
		}
	}

	private void incrementSubordinate(DistributionUser inviter) {
		int count = inviter.getSubordinateCount() == null ? 1 : inviter.getSubordinateCount() + 1;
		inviter.setSubordinateCount(count);
		if (!this.updateById(inviter)) {
			throw new ArynBusinessException("邀请人下级人数更新失败，请重试");
		}
	}

	private void decrementInviterSubordinateCount(String inviterUserId) {
		if (inviterUserId == null || inviterUserId.isBlank()) {
			return;
		}
		DistributionUser inviter = baseMapper.selectByUserIdForUpdate(inviterUserId);
		if (inviter == null) {
			return;
		}
		int count = inviter.getSubordinateCount() == null ? 0 : inviter.getSubordinateCount();
		inviter.setSubordinateCount(Math.max(0, count - 1));
		if (!this.updateById(inviter)) {
			throw new ArynBusinessException("邀请人下级人数更新失败，请重试");
		}
	}

	private boolean hasUnsettledAmount(DistributionUser user) {
		return nonZero(user.getAvailableCommission())
			|| nonZero(user.getPendingCommission())
			|| nonZero(user.getFrozenCommission())
			|| nonZero(user.getCommissionDebt());
	}

	private boolean nonZero(BigDecimal amount) {
		return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
	}

}
