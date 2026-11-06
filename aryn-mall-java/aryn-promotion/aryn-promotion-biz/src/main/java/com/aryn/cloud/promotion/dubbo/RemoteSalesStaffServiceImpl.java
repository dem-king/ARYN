package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.remote.RemoteSalesStaffService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 銷售员查询（Dubbo 实现）：供 user 域生成推广小程序码时校验会员是否为在职销售。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSalesStaffServiceImpl implements RemoteSalesStaffService {

	private final ISalesStaffService salesStaffService;

	@Override
	public SalesAttributionDTO getActiveStaffByMemberUserId(String tenantId, String memberUserId) {
		if (memberUserId == null || memberUserId.isBlank()) {
			return null;
		}
		String previous = ArynTenantContextHolder.getTenantId();
		try {
			if (tenantId != null && !tenantId.isBlank()) {
				ArynTenantContextHolder.setTenantId(tenantId);
			}
			SalesStaff staff = salesStaffService.getActiveByMemberUserId(memberUserId);
			if (staff == null) {
				return null;
			}
			SalesAttributionDTO dto = new SalesAttributionDTO();
			dto.setSalesStaffId(staff.getId());
			dto.setSalesStaffName(staff.getStaffName());
			return dto;
		}
		catch (RuntimeException e) {
			// 不吞异常：记录查询上下文后向上抛出，由调用方（推广码生成）自行兜底
			log.warn("查询在职销售失败 tenantId={}, memberUserId={}", tenantId, memberUserId, e);
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

}
