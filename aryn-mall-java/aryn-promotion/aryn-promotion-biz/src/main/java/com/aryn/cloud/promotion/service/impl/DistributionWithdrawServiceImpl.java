package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.enums.CommissionFlowTypeEnum;
import com.aryn.cloud.promotion.api.enums.DistributionWithdrawStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import com.aryn.cloud.promotion.service.WithdrawAccountCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 分销提现服务实现
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionWithdrawServiceImpl extends ServiceImpl<DistributionWithdrawMapper, DistributionWithdraw>
	implements IDistributionWithdrawService {

	private final IDistributionUserService distributionUserService;

	private final IDistributionConfigService distributionConfigService;

	private final IDistributionCommissionFlowService distributionCommissionFlowService;

	private final WithdrawAccountCipher withdrawAccountCipher;

	private String generateWithdrawNo() {
		String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		String randomPart = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
		return MallEventConstants.WITHDRAW_NO_PREFIX + datePart + randomPart;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean apply(String userId, DistributionWithdrawApplyDTO dto) {
		DistributionUser user = distributionUserService.getByUserId(userId);
		if (user == null) {
			throw new ArynBusinessException("分销用户不存在");
		}

		if (!MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(user.getStatus())) {
			throw new ArynBusinessException("分销用户已被禁用，无法提现");
		}

		DistributionConfig config = distributionConfigService.getActiveConfig();
		BigDecimal minAmount = config == null || config.getMinWithdrawAmount() == null
			? BigDecimal.ZERO : config.getMinWithdrawAmount();
		if (dto.getAmount().compareTo(minAmount) < 0) {
			throw new ArynBusinessException("提现金额低于最低提现限制" + minAmount + "元");
		}

		BigDecimal available = user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission();
		if (available.compareTo(dto.getAmount()) < 0) {
			throw new ArynBusinessException("可提现佣金不足");
		}

		BigDecimal frozen = user.getFrozenCommission() == null ? BigDecimal.ZERO : user.getFrozenCommission();
		user.setAvailableCommission(available.subtract(dto.getAmount()));
		user.setFrozenCommission(frozen.add(dto.getAmount()));
		boolean updated = distributionUserService.updateById(user);
		if (!updated) {
			throw new ArynBusinessException("佣金冻结失败，请重试");
		}

		DistributionWithdraw withdraw = new DistributionWithdraw();
		withdraw.setWithdrawNo(generateWithdrawNo());
		withdraw.setUserId(userId);
		withdraw.setAmount(dto.getAmount());
		withdraw.setStatus(DistributionWithdrawStatusEnum.STATUS_0.getCode());
		withdraw.setAccountType(dto.getAccountType());
		withdraw.setAccountName(dto.getAccountName());
		withdraw.setAccountNo(withdrawAccountCipher.encrypt(dto.getAccountNo()));
		withdraw.setRemark(dto.getRemark());

		log.info("提现申请成功 userId={}, withdrawNo={}, amount={}", userId, withdraw.getWithdrawNo(), dto.getAmount());
		if (!this.save(withdraw)) {
			throw new ArynBusinessException("提现申请保存失败，请重试");
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean audit(DistributionWithdrawAuditDTO dto) {
		DistributionWithdraw withdraw = this.getById(dto.getId());
		if (withdraw == null) {
			throw new ArynBusinessException("提现单不存在");
		}
		if (!DistributionWithdrawStatusEnum.STATUS_0.getCode().equals(withdraw.getStatus())) {
			throw new ArynBusinessException("仅待审核提现单可审核");
		}

		DistributionUser user = distributionUserService.getByUserId(withdraw.getUserId());
		if (user == null) {
			throw new ArynBusinessException("分销用户不存在");
		}

		BigDecimal frozen = user.getFrozenCommission() == null ? BigDecimal.ZERO : user.getFrozenCommission();
		if (frozen.compareTo(withdraw.getAmount()) < 0) {
			throw new ArynBusinessException("冻结佣金不足，无法审核");
		}

		if (DistributionWithdrawStatusEnum.STATUS_1.getCode().equals(dto.getStatus())) {
			if (dto.getPayoutNo() == null || dto.getPayoutNo().isBlank()) {
				throw new ArynBusinessException("审核通过必须填写打款流水号");
			}
			BigDecimal withdrawn = user.getWithdrawnCommission() == null
				? withdraw.getAmount() : user.getWithdrawnCommission().add(withdraw.getAmount());
			user.setFrozenCommission(frozen.subtract(withdraw.getAmount()));
			user.setWithdrawnCommission(withdrawn);

			boolean updated = distributionUserService.updateById(user);
			if (!updated) {
				throw new ArynBusinessException("佣金扣减失败，请重试");
			}

			DistributionCommissionFlow flow = new DistributionCommissionFlow();
			flow.setUserId(withdraw.getUserId());
			flow.setBizOrderId(withdraw.getId());
			flow.setFlowType(CommissionFlowTypeEnum.EXPENSE.getCode());
			flow.setAmount(withdraw.getAmount());
			flow.setBalanceAfter(user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission());
			flow.setRemark("提现审核通过");
			if (!distributionCommissionFlowService.save(flow)) {
				throw new ArynBusinessException("提现佣金流水保存失败");
			}
			String operator = currentOperator();
			withdraw.setPayoutNo(dto.getPayoutNo().trim());
			withdraw.setPayoutTime(LocalDateTime.now());
			withdraw.setPayoutBy(operator);

			log.info("提现审核通过 withdrawId={}, withdrawNo={}, userId={}, amount={}",
				dto.getId(), withdraw.getWithdrawNo(), withdraw.getUserId(), withdraw.getAmount());
		}
		else if (DistributionWithdrawStatusEnum.STATUS_2.getCode().equals(dto.getStatus())) {
			BigDecimal available = user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission();
			user.setFrozenCommission(frozen.subtract(withdraw.getAmount()));
			user.setAvailableCommission(available.add(withdraw.getAmount()));

			boolean updated = distributionUserService.updateById(user);
			if (!updated) {
				throw new ArynBusinessException("佣金解冻失败，请重试");
			}

			log.info("提现审核拒绝 withdrawId={}, withdrawNo={}, userId={}, amount={}, reason={}",
				dto.getId(), withdraw.getWithdrawNo(), withdraw.getUserId(), withdraw.getAmount(), dto.getRejectReason());
		}

		withdraw.setStatus(dto.getStatus());
		withdraw.setRejectReason(dto.getRejectReason());
		withdraw.setAuditTime(LocalDateTime.now());
		withdraw.setAuditBy(currentOperator());
		if (!this.updateById(withdraw)) {
			throw new ArynBusinessException("提现单状态更新失败，请重试");
		}
		return Boolean.TRUE;
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

	private String currentOperator() {
		try {
			return SecurityUtils.getUser() == null ? "system" : SecurityUtils.getUser().getUsername();
		}
		catch (Exception e) {
			log.warn("获取审核人信息失败", e);
			return "system";
		}
	}

}
