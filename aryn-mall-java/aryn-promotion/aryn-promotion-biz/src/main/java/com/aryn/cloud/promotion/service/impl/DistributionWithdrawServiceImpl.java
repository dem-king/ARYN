package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.enums.DistributionWithdrawStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import com.aryn.cloud.promotion.service.WithdrawAccountCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分销提现服务实现
 * <p>
 * 2026-11 起分销佣金不能提现，改为兑换优惠券：申请/审核接口下线并返回明确业务错误，
 * 仅保留历史单据的分页与详情只读查询，以及历史收款账号重加密能力。
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionWithdrawServiceImpl extends ServiceImpl<DistributionWithdrawMapper, DistributionWithdraw>
	implements IDistributionWithdrawService {

	/** 提现申请下线提示 */
	private static final String WITHDRAW_APPLY_OFFLINE_MESSAGE = "提现功能已下线，佣金可兑换优惠券";

	/** 提现审核下线提示 */
	private static final String WITHDRAW_AUDIT_OFFLINE_MESSAGE = "提现功能已下线，仅保留历史查询";

	private final IDistributionUserService distributionUserService;

	private final WithdrawAccountCipher withdrawAccountCipher;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean apply(String userId, DistributionWithdrawApplyDTO dto) {
		log.warn("提现申请接口已下线，拒绝调用 userId={}", userId);
		throw new ArynBusinessException(WITHDRAW_APPLY_OFFLINE_MESSAGE);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean audit(DistributionWithdrawAuditDTO dto) {
		log.warn("提现审核接口已下线，拒绝调用 withdrawId={}", dto == null ? null : dto.getId());
		throw new ArynBusinessException(WITHDRAW_AUDIT_OFFLINE_MESSAGE);
	}

	@Override
	public DistributionCenterVO getCenter(String userId) {
		DistributionUser user = distributionUserService.getByUserId(userId);
		DistributionCenterVO vo = new DistributionCenterVO();
		if (user == null) {
			vo.setTotalCommission(BigDecimal.ZERO);
			vo.setAvailableCommission(BigDecimal.ZERO);
			vo.setWithdrawnCommission(BigDecimal.ZERO);
			vo.setFrozenCommission(BigDecimal.ZERO);
			vo.setPendingCommission(BigDecimal.ZERO);
			vo.setCommissionDebt(BigDecimal.ZERO);
			vo.setInviteUserCount(0);
			vo.setPendingWithdrawCount(0L);
			return vo;
		}
		vo.setTotalCommission(user.getTotalCommission() == null ? BigDecimal.ZERO : user.getTotalCommission());
		vo.setAvailableCommission(user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission());
		vo.setWithdrawnCommission(user.getWithdrawnCommission() == null ? BigDecimal.ZERO : user.getWithdrawnCommission());
		vo.setFrozenCommission(user.getFrozenCommission() == null ? BigDecimal.ZERO : user.getFrozenCommission());
		vo.setPendingCommission(user.getPendingCommission() == null ? BigDecimal.ZERO : user.getPendingCommission());
		vo.setCommissionDebt(user.getCommissionDebt() == null ? BigDecimal.ZERO : user.getCommissionDebt());
		vo.setInviteUserCount(user.getSubordinateCount() == null ? 0 : user.getSubordinateCount());
		Long pending = this.count(Wrappers.<DistributionWithdraw>lambdaQuery()
			.eq(DistributionWithdraw::getUserId, userId)
			.eq(DistributionWithdraw::getStatus, DistributionWithdrawStatusEnum.STATUS_0.getCode()));
		vo.setPendingWithdrawCount(pending);
		return vo;
	}

	@Override
	public IPage<DistributionWithdrawVO> getAdminPage(Page<DistributionWithdraw> page, DistributionWithdraw query) {
		return baseMapper.selectAdminPage(page, query).convert(this::toVo);
	}

	@Override
	public DistributionWithdrawVO getDetail(String id) {
		return toVo(this.getById(id));
	}

	@Override
	public IPage<DistributionWithdrawVO> getUserPage(Page<DistributionWithdraw> page, String userId) {
		return this.page(page, Wrappers.<DistributionWithdraw>lambdaQuery()
			.eq(DistributionWithdraw::getUserId, userId)
			.orderByDesc(DistributionWithdraw::getCreateTime)).convert(this::toVo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int reencryptLegacyAccounts() {
		final int batchSize = 200;
		int updatedCount = 0;
		while (true) {
			List<DistributionWithdraw> legacyAccounts = baseMapper.selectLegacyAccounts(batchSize);
			if (legacyAccounts.isEmpty()) {
				return updatedCount;
			}
			for (DistributionWithdraw withdraw : legacyAccounts) {
				withdraw.setAccountNo(withdrawAccountCipher.encrypt(withdraw.getAccountNo()));
				if (!this.updateById(withdraw)) {
					throw new ArynBusinessException("历史提现账号重加密失败，请重试");
				}
				updatedCount++;
			}
		}
	}

	private DistributionWithdrawVO toVo(DistributionWithdraw withdraw) {
		if (withdraw == null) {
			return null;
		}
		return new DistributionWithdrawVO()
			.setId(withdraw.getId())
			.setWithdrawNo(withdraw.getWithdrawNo())
			.setUserId(withdraw.getUserId())
			.setAmount(withdraw.getAmount())
			.setStatus(withdraw.getStatus())
			.setAccountType(withdraw.getAccountType())
			.setAccountName(withdraw.getAccountName())
			.setAccountNo(withdrawAccountCipher.maskStored(withdraw.getAccountNo()))
			.setRejectReason(withdraw.getRejectReason())
			.setRemark(withdraw.getRemark())
			.setAuditTime(withdraw.getAuditTime())
			.setAuditBy(withdraw.getAuditBy())
			.setPayoutNo(withdraw.getPayoutNo())
			.setPayoutTime(withdraw.getPayoutTime())
			.setPayoutBy(withdraw.getPayoutBy())
			.setCreateTime(withdraw.getCreateTime())
			.setUpdateTime(withdraw.getUpdateTime());
	}

}
