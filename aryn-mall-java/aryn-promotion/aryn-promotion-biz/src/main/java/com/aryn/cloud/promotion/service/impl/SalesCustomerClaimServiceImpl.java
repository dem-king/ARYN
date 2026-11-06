package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerClaimStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesCustomerClaimVO;
import com.aryn.cloud.promotion.mapper.SalesCustomerClaimMapper;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 客户待转交认领服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesCustomerClaimServiceImpl extends ServiceImpl<SalesCustomerClaimMapper, SalesCustomerClaim>
	implements ISalesCustomerClaimService {

	private final ISalesConfigService salesConfigService;

	private final ISalesStaffService salesStaffService;

	private final SalesBindWriter salesBindWriter;

	private final SalesUserEnricher salesUserEnricher;

	@Override
	public IPage<SalesCustomerClaimVO> selectAdminPage(Page<SalesCustomerClaim> page, SalesCustomerClaim query) {
		SalesCustomerClaim condition = query == null ? new SalesCustomerClaim() : query;
		IPage<SalesCustomerClaim> claimPage = this.page(page, Wrappers.<SalesCustomerClaim>lambdaQuery()
			.eq(notBlank(condition.getStatus()), SalesCustomerClaim::getStatus, condition.getStatus())
			.eq(notBlank(condition.getSalesStaffId()), SalesCustomerClaim::getSalesStaffId,
					condition.getSalesStaffId())
			.eq(notBlank(condition.getCustomerUserId()), SalesCustomerClaim::getCustomerUserId,
					condition.getCustomerUserId())
			.orderByDesc(SalesCustomerClaim::getClaimTime));
		Map<String, SalesStaff> staffMap = salesStaffService.getStaffMap(
				claimPage.getRecords().stream().map(SalesCustomerClaim::getSalesStaffId).toList());
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(
				claimPage.getRecords().stream().map(SalesCustomerClaim::getCustomerUserId).toList());
		return claimPage.convert(claim -> {
			SalesCustomerClaimVO vo = new SalesCustomerClaimVO();
			BeanUtils.copyProperties(claim, vo);
			SalesStaff staff = staffMap.get(claim.getSalesStaffId());
			if (staff != null) {
				vo.setStaffName(staff.getStaffName());
			}
			UserInfoVO user = userMap.get(claim.getCustomerUserId());
			if (user != null) {
				vo.setNickname(user.getNickname());
			}
			return vo;
		});
	}

	@Override
	public SalesCustomerClaim getPendingClaim(String customerUserId, String salesStaffId) {
		if (customerUserId == null || customerUserId.isBlank() || salesStaffId == null || salesStaffId.isBlank()) {
			return null;
		}
		return this.getOne(Wrappers.<SalesCustomerClaim>lambdaQuery()
			.eq(SalesCustomerClaim::getCustomerUserId, customerUserId)
			.eq(SalesCustomerClaim::getSalesStaffId, salesStaffId)
			.eq(SalesCustomerClaim::getStatus, SalesCustomerClaimStatusEnum.STATUS_0.getCode())
			.gt(SalesCustomerClaim::getExpireTime, LocalDateTime.now())
			.last("limit 1"));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SalesCustomerClaim upsertClaim(String customerUserId, String salesStaffId, String source) {
		if (customerUserId == null || customerUserId.isBlank() || salesStaffId == null || salesStaffId.isBlank()) {
			return null;
		}
		SalesConfig config = salesConfigService.getActiveConfigOrDefaults();
		int expireDays = config.getClaimExpireDays() == null
			? SalesConfigServiceImpl.DEFAULT_CLAIM_EXPIRE_DAYS : config.getClaimExpireDays();
		LocalDateTime now = LocalDateTime.now();
		SalesCustomerClaim claim = this.getOne(Wrappers.<SalesCustomerClaim>lambdaQuery()
			.eq(SalesCustomerClaim::getCustomerUserId, customerUserId)
			.last("limit 1"));
		if (claim == null) {
			claim = new SalesCustomerClaim();
			claim.setCustomerUserId(customerUserId);
			claim.setSalesStaffId(salesStaffId);
			claim.setSource(source);
			claim.setStatus(SalesCustomerClaimStatusEnum.STATUS_0.getCode());
			claim.setClaimTime(now);
			claim.setExpireTime(now.plusDays(expireDays));
			try {
				if (!this.save(claim)) {
					throw new ArynBusinessException("客户认领保存失败");
				}
				log.info("客户认领创建 customerUserId={}, salesStaffId={}, source={}", customerUserId, salesStaffId,
						source);
				return claim;
			}
			catch (DuplicateKeyException e) {
				// 并发重复点击：回读后按更新处理
				claim = this.getOne(Wrappers.<SalesCustomerClaim>lambdaQuery()
					.eq(SalesCustomerClaim::getCustomerUserId, customerUserId)
					.last("limit 1"));
				if (claim == null) {
					throw e;
				}
			}
		}
		claim.setSalesStaffId(salesStaffId);
		claim.setSource(source);
		claim.setStatus(SalesCustomerClaimStatusEnum.STATUS_0.getCode());
		claim.setClaimTime(now);
		claim.setExpireTime(now.plusDays(expireDays));
		if (!this.updateById(claim)) {
			throw new ArynBusinessException("客户认领更新失败");
		}
		log.info("客户认领更新（以最新一次点击为准） customerUserId={}, salesStaffId={}, source={}",
				customerUserId, salesStaffId, source);
		return claim;
	}

	@Override
	public boolean markTransferred(String claimId) {
		return salesBindWriter.markClaimTransferred(claimId);
	}

	@Override
	public int invalidatePendingClaims(String customerUserId) {
		return salesBindWriter.invalidatePendingClaims(customerUserId);
	}

	@Override
	public int invalidatePendingClaimsByStaff(String salesStaffId) {
		return salesBindWriter.invalidatePendingClaimsByStaff(salesStaffId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int expireClaims() {
		SalesCustomerClaim update = new SalesCustomerClaim();
		update.setStatus(SalesCustomerClaimStatusEnum.STATUS_2.getCode());
		int rows = this.baseMapper.update(update, Wrappers.<SalesCustomerClaim>lambdaUpdate()
			.eq(SalesCustomerClaim::getStatus, SalesCustomerClaimStatusEnum.STATUS_0.getCode())
			.le(SalesCustomerClaim::getExpireTime, LocalDateTime.now()));
		if (rows > 0) {
			log.info("客户认领过期失效 {} 条", rows);
		}
		return rows;
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
