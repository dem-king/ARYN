package com.aryn.cloud.common.dubbo.filter;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcServiceContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArynDubboRequestFilterTest {

	private final ArynDubboRequestFilter filter = new ArynDubboRequestFilter();

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void propagatesTenantOnConsumerSide() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenReturn(expected);
		ArynTenantContextHolder.setTenantId("tenant-consumer");

		try (MockedStatic<RpcContext> context = consumerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		verify(invocation).setAttachment("tenantId", "tenant-consumer");
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-consumer");
	}

	@Test
	void clearsTenantAfterSuccessfulProviderInvocation() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = providerInvocation("tenant-provider");
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenAnswer(ignored -> {
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-provider");
			return expected;
		});

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void clearsTenantAfterFailedProviderInvocation() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = providerInvocation("tenant-provider");
		when(invoker.invoke(invocation)).thenThrow(new RpcException("provider failure"));

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThatThrownBy(() -> filter.invoke(invoker, invocation))
					.isInstanceOf(RpcException.class)
					.hasMessage("provider failure");
		}

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private Invocation providerInvocation(String tenantId) {
		Invocation invocation = mock(Invocation.class);
		when(invocation.getAttachment("tenantId")).thenReturn(tenantId);
		return invocation;
	}

	private MockedStatic<RpcContext> consumerContext() {
		RpcServiceContext serviceContext = mock(RpcServiceContext.class);
		when(serviceContext.isConsumerSide()).thenReturn(true);
		MockedStatic<RpcContext> context = mockStatic(RpcContext.class);
		context.when(RpcContext::getServiceContext).thenReturn(serviceContext);
		return context;
	}

	private MockedStatic<RpcContext> providerContext() {
		RpcServiceContext serviceContext = mock(RpcServiceContext.class);
		when(serviceContext.isConsumerSide()).thenReturn(false);
		MockedStatic<RpcContext> context = mockStatic(RpcContext.class);
		context.when(RpcContext::getServiceContext).thenReturn(serviceContext);
		return context;
	}

}
