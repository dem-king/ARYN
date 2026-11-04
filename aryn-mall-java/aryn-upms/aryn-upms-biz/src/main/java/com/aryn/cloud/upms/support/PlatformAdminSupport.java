package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * 平台管理员会话判定。
 *
 * <p>平台管理员是<b>用户级</b>身份：{@code sys_user.platform_admin = '1'} 且所属租户为平台租户
 * （{@code sys_tenant.tenant_type = '1'}）。登录时计算后写入会话快照 {@link ArynUser#getPlatformAdmin()}，
 * 之后所有「当前会话是否平台管理员」的判断都用会话值，避免逐请求查库。</p>
 *
 * <p>注意与「平台租户」区分：平台租户下的租户管理员（如平台租户里的普通业务管理员）不是平台管理员，
 * 不能切换租户、不能操作平台配置，菜单按租户套餐过滤。</p>
 */
@Component
public class PlatformAdminSupport {

	/**
	 * 当前会话是否为平台管理员；未登录或存量会话缺少标记时按非平台管理员处理（重新登录即可刷新）。
	 */
	public boolean isCurrentPlatformAdmin() {
		ArynUser user = SecurityUtils.getUser();
		return user != null && Boolean.TRUE.equals(user.getPlatformAdmin());
	}

}
