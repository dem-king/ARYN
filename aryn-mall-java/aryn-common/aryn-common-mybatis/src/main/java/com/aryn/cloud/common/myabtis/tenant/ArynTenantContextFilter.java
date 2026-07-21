
package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.core.constant.CommonConstants;
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
		String token = request.getHeader(CommonConstants.TOKEN_KEY);
		if (StringUtils.hasText(token) && Objects.nonNull(SecurityUtils.getUser())) {
			// 判断是否是平台管理员 平台管理员可以切换租户
			if (SecurityUtils.getUser().getTenantId().equals(CommonConstants.PLATFORM_TENANT_ID)
					&& StringUtils.hasText(tenantId)) {
				ArynTenantContextHolder.setTenantId(tenantId);
			}
			else {
				ArynTenantContextHolder.setTenantId(SecurityUtils.getUser().getTenantId());
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

}
