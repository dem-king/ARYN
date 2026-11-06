package com.aryn.cloud.upms.support;

import com.aryn.cloud.upms.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 平台管理员在旁路视图（登录日志、在线用户）中的可见性控制。
 *
 * <p>平台管理员账号是系统内置账号，租户管理员不应在审计/在线列表里看到它的存在。
 * 登录日志与在线记录只保存用户名，因此按「平台管理员账号用户名集合」过滤；
 * 当前会话本身是平台管理员时不过滤（平台管理员仍需完整审计能力）。</p>
 *
 * <p>用户名在有效账号中全局唯一（{@code uk_sys_user_username}），按用户名过滤可靠；
 * 平台管理员账号不允许经员工账号接口改名，故历史记录也能被稳定过滤。</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformAdminVisibilitySupport {

	private final PlatformAdminSupport platformAdminSupport;

	private final SysUserMapper sysUserMapper;

	/**
	 * 当前会话需要隐藏的平台管理员用户名集合；平台管理员会话返回空集合。
	 *
	 * <p>租户拦截器会限定当前租户：平台管理员账号只存在于平台租户，普通租户查询为空集合，
	 * 不会误伤同名业务账号。</p>
	 */
	public Set<String> hiddenPlatformAdminUsernames() {
		if (platformAdminSupport.isCurrentPlatformAdmin()) {
			return Collections.emptySet();
		}
		return sysUserMapper.selectPlatformAdminUsernames()
			.stream()
			.filter(name -> name != null && !name.isBlank())
			.collect(Collectors.toSet());
	}

}
