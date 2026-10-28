
package com.aryn.cloud.upms.dubbo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.upms.api.dto.TenantAccessDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.upms.service.ISysTenantService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteTenantServiceImpl implements RemoteTenantService {

	private final ISysTenantService sysTenantService;

	@Override
	public List<SysTenant> list() {
		return sysTenantService
			.list(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getStatus, CommonConstants.NORMAL_STATUS));
	}

	@Override
	public TenantAccessDTO getAccess(String tenantId) {
		if (!StringUtils.hasText(tenantId)) {
			return null;
		}
		SysTenant tenant = sysTenantService.getById(tenantId);
		if (tenant == null) {
			return null;
		}
		TenantAccessDTO access = new TenantAccessDTO();
		access.setTenantId(tenant.getId());
		access.setStatus(tenant.getStatus());
		access.setAuthBeginTime(tenant.getAuthBeginTime());
		access.setAuthEndTime(tenant.getAuthEndTime());
		return access;
	}

}
