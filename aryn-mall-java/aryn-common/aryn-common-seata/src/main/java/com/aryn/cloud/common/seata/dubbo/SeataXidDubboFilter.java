package com.aryn.cloud.common.seata.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.seata.core.context.RootContext;

/**
 * Seata XID 跨 Apache Dubbo 3 透传过滤器。
 *
 * <p>seata-all 2.5 只内置 Alibaba Dubbo 的过滤器（{@code com.alibaba.dubbo.rpc.Filter}），
 * 没有注册 Apache Dubbo 3 的 {@code org.apache.dubbo.rpc.Filter} SPI，因此
 * {@code @GlobalTransactional} 发起的跨服务 Dubbo 调用不会携带 XID，下游服务的事务不会被
 * 注册为 Seata 分支，出现「上游回滚但下游已提交」的脏数据（积分扣减/返还、库存、优惠券都受影响）。
 *
 * <p>本过滤器在消费方把 {@link RootContext} 中的 XID 写入调用附件，在提供方绑定 XID 并在调用结束后
 * 还原调用线程原有上下文，使接入 Seata 的各业务域写操作加入同一个全局事务。
 *
 * <p>无全局事务时（{@code seata.enabled=false} 的 Boot 单体模式、或普通只读调用）XID 为空，
 * 过滤器直接透传，不改变既有行为。
 */
@Activate(group = { CommonConstants.CONSUMER, CommonConstants.PROVIDER }, order = -10000)
public class SeataXidDubboFilter implements Filter {

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		if (RpcContext.getServiceContext().isConsumerSide()) {
			return invokeOnConsumerSide(invoker, invocation);
		}
		return invokeOnProviderSide(invoker, invocation);
	}

	private Result invokeOnConsumerSide(Invoker<?> invoker, Invocation invocation) {
		String xid = RootContext.getXID();
		if (hasText(xid)) {
			invocation.setAttachment(RootContext.KEY_XID, xid);
		}
		return invoker.invoke(invocation);
	}

	private Result invokeOnProviderSide(Invoker<?> invoker, Invocation invocation) {
		String xid = invocation.getAttachment(RootContext.KEY_XID);
		if (!hasText(xid)) {
			return invoker.invoke(invocation);
		}
		String previousXid = RootContext.getXID();
		if (xid.equals(previousXid)) {
			// injvm/同线程调用：XID 已绑定，调用结束后由外层统一收尾
			return invoker.invoke(invocation);
		}
		RootContext.bind(xid);
		try {
			return invoker.invoke(invocation);
		}
		finally {
			if (hasText(previousXid)) {
				RootContext.bind(previousXid);
			}
			else {
				RootContext.unbind();
			}
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
