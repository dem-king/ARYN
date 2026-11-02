
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysTenantPackage;
import com.aryn.cloud.upms.mapper.SysTenantPackageMapper;
import com.aryn.cloud.upms.service.ISysTenantPackageService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户套餐
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:51
 */
@Service
@AllArgsConstructor
public class SysTenantPackageServiceImpl extends ServiceImpl<SysTenantPackageMapper, SysTenantPackage>
		implements ISysTenantPackageService {

	@Override
	public boolean save(SysTenantPackage entity) {
		rejectPlatformAppKey(entity);
		return super.save(entity);
	}

	@Override
	public boolean updateById(SysTenantPackage entity) {
		rejectPlatformAppKey(entity);
		return super.updateById(entity);
	}

	/**
	 * 平台应用菜单包含菜单管理、字典管理、代码生成等平台级能力，一旦进入套餐就会随建租户
	 * 整包下发给租户管理员角色，因此必须在写入套餐前拦住。
	 */
	private void rejectPlatformAppKey(SysTenantPackage tenantPackage) {
		if (tenantPackage == null || tenantPackage.getAppKey() == null) {
			return;
		}
		for (String appKey : tenantPackage.getAppKey()) {
			if (CommonConstants.PLATFORM_APP_KEY.equals(appKey)) {
				throw new ArynBusinessException("平台应用不可分配给租户套餐");
			}
		}
	}

}
