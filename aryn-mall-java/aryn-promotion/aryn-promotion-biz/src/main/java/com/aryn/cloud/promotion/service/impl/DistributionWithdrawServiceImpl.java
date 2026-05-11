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
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
		withdraw.setAccountNo(dto.getAccountNo());

		log.info("提现申请成功 userId={}, withdrawNo={}, amount={}", userId, withdraw.getWithdrawNo(), dto.getAmount());
		return this.save(withdraw);
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

		if (DistributionWithdrawStatusEnum.STATUS_1.getCode().equals(dto.getStatus())) {
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
			distributionCommissionFlowService.save(flow);

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
		try {
			withdraw.setAuditBy(SecurityUtils.getUser().getUsername());
		}
		catch (Exception e) {
			log.warn("获取审核人信息失败", e);
		}
		return this.updateById(withdraw);
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
			vo.setPendingWithdrawCount(0L);
			return vo;
		}
		vo.setTotalCommission(user.getTotalCommission() == null ? BigDecimal.ZERO : user.getTotalCommission());
		vo.setAvailableCommission(user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission());
		vo.setWithdrawnCommission(user.getWithdrawnCommission() == null ? BigDecimal.ZERO : user.getWithdrawnCommission());
		vo.setFrozenCommission(user.getFrozenCommission() == null ? BigDecimal.ZERO : user.getFrozenCommission());
		Long pending = this.count(Wrappers.<DistributionWithdraw>lambdaQuery()
			.eq(DistributionWithdraw::getUserId, userId)
			.eq(DistributionWithdraw::getStatus, DistributionWithdrawStatusEnum.STATUS_0.getCode()));
		vo.setPendingWithdrawCount(pending);
		return vo;
	}

	@Override
	public IPage<DistributionWithdraw> getUserPage(Page page, String userId) {
		return this.page(page, Wrappers.<DistributionWithdraw>lambdaQuery()
			.eq(DistributionWithdraw::getUserId, userId)
			.orderByDesc(DistributionWithdraw::getCreateTime));
	}

}
