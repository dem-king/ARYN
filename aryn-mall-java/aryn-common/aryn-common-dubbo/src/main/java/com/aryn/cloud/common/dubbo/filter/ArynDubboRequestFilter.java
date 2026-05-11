
package com.aryn.cloud.common.dubbo.filter;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;

/**
 * Sa-Token 整合 Dubbo过滤器
 * @author 雨滴kian
 */
@Slf4j
@Activate(group = { CommonConstants.PROVIDER, CommonConstants.CONSUMER }, order = Integer.MAX_VALUE)
public class ArynDubboRequestFilter implements Filter {

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		// 判断是消费者 还是 服务提供者
		if (RpcContext.getServiceContext().isConsumerSide()) {
			// 传递租户ID
			invocation.setAttachment("tenantId", ArynTenantContextHolder.getTenantId());
		}
		else {
			ArynTenantContextHolder.setTenantId(invocation.getAttachment("tenantId"));
		}
		return invoker.invoke(invocation);
	}

}