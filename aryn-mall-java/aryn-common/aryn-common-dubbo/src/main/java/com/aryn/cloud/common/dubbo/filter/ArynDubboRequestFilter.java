
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
			// 提供方执行完必须还原调用前的租户，而不是一律清空：
			// Boot 模式下 injvm 调用与服务调用方共用同一线程，清空会把调用方（HTTP 请求线程）
			// 的租户一并抹掉，导致后续 insert 被注入 tenant_id = NULL。
			// 真实 RPC 场景提供方线程原本没有租户，还原等价于清空，仍可避免线程复用造成租户串号。
			String callerTenantId = ArynTenantContextHolder.getTenantId();
			ArynTenantContextHolder.setTenantId(invocation.getAttachment("tenantId"));
			try {
				return invoker.invoke(invocation);
			}
			finally {
				if (callerTenantId == null) {
					ArynTenantContextHolder.removeTenantId();
				}
				else {
					ArynTenantContextHolder.setTenantId(callerTenantId);
				}
			}
		}
		return invoker.invoke(invocation);
	}

}
