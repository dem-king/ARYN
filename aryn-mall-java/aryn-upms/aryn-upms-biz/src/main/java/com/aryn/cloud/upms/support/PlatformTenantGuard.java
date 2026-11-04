package com.aryn.cloud.upms.support;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 平台级租户配置防线。
 *
 * <p>租户、套餐、租户菜单和全局菜单都是跨租户配置，仅凭权限串不足以区分「平台管理员」与
 * 「被套餐授予了同名权限的租户管理员」。这些接口必须在权限校验之外再校验会话属于平台管理员，
 * 且必须来自管理端（TOB），避免普通租户会话通过 URL/请求体指定其它租户。</p>
 *
 * <p>平台管理员是用户级身份（{@code sys_user.platform_admin = '1'} 且所属租户为平台租户），
 * 由登录时写入会话快照；平台租户里的租户管理员不是平台管理员。</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformTenantGuard {

	private final PlatformAdminSupport platformAdminSupport;

	public ArynUser requirePlatformTenant() {
		ArynUser user = SecurityUtils.getUser();
		if (user == null) {
			throw new ArynBusinessException(403, "当前会话未登录");
		}
		DeviceTypeEnum deviceType = user.getDeviceType();
		if (deviceType == null) {
			deviceType = SecurityUtils.parseDeviceType(StpUtil.getLoginDeviceType());
		}
		if (deviceType != DeviceTypeEnum.TOB) {
			throw new ArynBusinessException(403, "仅管理端可操作租户配置");
		}
		if (!platformAdminSupport.isCurrentPlatformAdmin()) {
			throw new ArynBusinessException(403, "仅平台管理员可操作租户配置");
		}
		return user;
	}

}
