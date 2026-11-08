
package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
public class ArynTenantContextFilter extends GenericFilterBean {

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
			throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		// 获取请求头 租户id
		String tenantId = request.getHeader(CommonConstants.TENANT_ID);
		// 会话租户是唯一事实来源，与凭证放在请求头还是请求参数无关：
		// 只按 satoken 请求头是否存在决定是否读取会话，会让参数凭证（Sa-Token
		// 默认从请求参数读 token）下的请求退回「信任 tenant-id 请求头」分支，
		// 非平台用户即可带着 A 租户会话把上下文切到 B 租户，越权读写 B 的数据。
		ArynUser user = resolveSessionUser();
		if (Objects.nonNull(user)) {
			// 判断是否是平台管理员（用户级标记，登录时写入会话） 平台管理员可以切换租户；
			// 平台租户里的租户管理员不是平台管理员，不能切换租户
			if (Boolean.TRUE.equals(user.getPlatformAdmin())
					&& StringUtils.hasText(tenantId)) {
				ArynTenantContextHolder.setTenantId(tenantId);
			}
			else {
				ArynTenantContextHolder.setTenantId(user.getTenantId());
			}
		}
		else {
			ArynTenantContextHolder.setTenantId(tenantId);
		}
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	/**
	 * 解析当前请求的会话用户（未登录返回 null）。
	 *
	 * <p>独立成方法只为测试可注入会话；生产路径固定读 Sa-Token 会话。
	 */
	protected ArynUser resolveSessionUser() {
		return SecurityUtils.getUser();
	}

}
