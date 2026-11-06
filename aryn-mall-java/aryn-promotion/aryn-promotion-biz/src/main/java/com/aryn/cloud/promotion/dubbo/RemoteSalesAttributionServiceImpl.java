package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerClaimStatusEnum;
import com.aryn.cloud.promotion.api.remote.RemoteSalesAttributionService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 订单归属销售解析（Dubbo 实现）
 * <p>
 * 判定顺序：已绑定销售（在职）→ 未过期待转交认领中的销售（在职）→ 无。
 * 显式按入参 tenantId 设置租户上下文，避免消费方线程上下文缺失导致跨租户/全租户查询。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSalesAttributionServiceImpl implements RemoteSalesAttributionService {

	private final ISalesCustomerBindService salesCustomerBindService;

	private final ISalesCustomerClaimService salesCustomerClaimService;

	private final ISalesStaffService salesStaffService;

	@Override
	public SalesAttributionDTO resolve(String tenantId, String userId) {
		if (userId == null || userId.isBlank()) {
			return null;
		}
		String previous = ArynTenantContextHolder.getTenantId();
		try {
			if (tenantId != null && !tenantId.isBlank()) {
				ArynTenantContextHolder.setTenantId(tenantId);
			}
			SalesStaff staff = resolveBoundStaff(userId);
			if (staff == null) {
				staff = resolveClaimedStaff(userId);
			}
			return toDTO(staff);
		}
		catch (RuntimeException e) {
			// 不吞异常：记录归属解析上下文后向上抛出，由调用方（下单/推广码）自行兜底
			log.warn("解析订单归属销售失败 tenantId={}, userId={}", tenantId, userId, e);
			throw e;
		}
		finally {
			if (previous == null || previous.isBlank()) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previous);
			}
		}
	}

	private SalesStaff resolveBoundStaff(String userId) {
		SalesCustomerBind bind = salesCustomerBindService.getByCustomerUserId(userId);
		if (bind == null) {
			return null;
		}
		return salesStaffService.getActiveById(bind.getSalesStaffId());
	}

	private SalesStaff resolveClaimedStaff(String userId) {
		SalesCustomerClaim claim = salesCustomerClaimService
			.getOne(Wrappers.<SalesCustomerClaim>lambdaQuery()
				.eq(SalesCustomerClaim::getCustomerUserId, userId)
				.eq(SalesCustomerClaim::getStatus, SalesCustomerClaimStatusEnum.STATUS_0.getCode())
				.gt(SalesCustomerClaim::getExpireTime, LocalDateTime.now())
				.orderByDesc(SalesCustomerClaim::getClaimTime)
				.last("limit 1"));
		if (claim == null) {
			return null;
		}
		return salesStaffService.getActiveById(claim.getSalesStaffId());
	}

	private SalesAttributionDTO toDTO(SalesStaff staff) {
		if (staff == null) {
			return null;
		}
		SalesAttributionDTO dto = new SalesAttributionDTO();
		dto.setSalesStaffId(staff.getId());
		dto.setSalesStaffName(staff.getStaffName());
		return dto;
	}

}
