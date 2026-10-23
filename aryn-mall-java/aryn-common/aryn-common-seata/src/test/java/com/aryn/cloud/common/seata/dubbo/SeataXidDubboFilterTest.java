package com.aryn.cloud.common.seata.dubbo;

import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcServiceContext;
import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeataXidDubboFilterTest {

	private final SeataXidDubboFilter filter = new SeataXidDubboFilter();

	@AfterEach
	void clearXid() {
		RootContext.unbind();
	}

	@Test
	void propagatesXidOnConsumerSide() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenReturn(expected);
		RootContext.bind("xid-consumer:1001");

		try (MockedStatic<RpcContext> context = consumerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		verify(invocation).setAttachment(RootContext.KEY_XID, "xid-consumer:1001");
	}

	@Test
	void doesNotSetAttachmentWithoutGlobalTransaction() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenReturn(expected);

		try (MockedStatic<RpcContext> context = consumerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		verify(invocation, never()).setAttachment(RootContext.KEY_XID, null);
		verify(invocation, never()).setAttachment(org.mockito.ArgumentMatchers.eq(RootContext.KEY_XID),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void bindsXidOnProviderSideAndClearsAfterInvocation() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		when(invocation.getAttachment(RootContext.KEY_XID)).thenReturn("xid-provider:2001");
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenAnswer(ignored -> {
			assertThat(RootContext.getXID()).isEqualTo("xid-provider:2001");
			return expected;
		});

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		assertThat(RootContext.getXID()).isNull();
	}

	@Test
	void restoresCallerXidAfterProviderInvocation() {
		// Boot 模式 injvm：提供方与调用方同线程，调用前线程上已有 XID
		RootContext.bind("xid-caller:3001");
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		when(invocation.getAttachment(RootContext.KEY_XID)).thenReturn("xid-provider:3002");
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenAnswer(ignored -> {
			assertThat(RootContext.getXID()).isEqualTo("xid-provider:3002");
			return expected;
		});

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		assertThat(RootContext.getXID()).isEqualTo("xid-caller:3001");
	}

	@Test
	void clearsXidAfterFailedProviderInvocation() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		when(invocation.getAttachment(RootContext.KEY_XID)).thenReturn("xid-provider:4001");
		when(invoker.invoke(invocation)).thenThrow(new RpcException("provider failure"));

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThatThrownBy(() -> filter.invoke(invoker, invocation)).isInstanceOf(RpcException.class)
				.hasMessage("provider failure");
		}

		assertThat(RootContext.getXID()).isNull();
	}

	@Test
	void passesThroughWhenProviderInvocationHasNoXid() {
		Invoker<?> invoker = mock(Invoker.class);
		Invocation invocation = mock(Invocation.class);
		Result expected = mock(Result.class);
		when(invoker.invoke(invocation)).thenReturn(expected);

		try (MockedStatic<RpcContext> context = providerContext()) {
			assertThat(filter.invoke(invoker, invocation)).isSameAs(expected);
		}

		assertThat(RootContext.getXID()).isNull();
	}

	@Test
	void registersXidFilterAsDubboExtension() throws IOException {
		try (InputStream stream = getClass().getClassLoader()
			.getResourceAsStream("META-INF/dubbo/org.apache.dubbo.rpc.Filter")) {
			assertThat(stream).isNotNull();
			String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(descriptor).contains(
					"seataXidDubboFilter=com.aryn.cloud.common.seata.dubbo.SeataXidDubboFilter");
		}
	}

	private MockedStatic<RpcContext> consumerContext() {
		RpcServiceContext serviceContext = mock(RpcServiceContext.class);
		when(serviceContext.isConsumerSide()).thenReturn(true);
		return mockServiceContext(serviceContext);
	}

	private MockedStatic<RpcContext> providerContext() {
		RpcServiceContext serviceContext = mock(RpcServiceContext.class);
		when(serviceContext.isConsumerSide()).thenReturn(false);
		return mockServiceContext(serviceContext);
	}

	private MockedStatic<RpcContext> mockServiceContext(RpcServiceContext serviceContext) {
		MockedStatic<RpcContext> context = mockStatic(RpcContext.class);
		context.when(RpcContext::getServiceContext).thenReturn(serviceContext);
		return context;
	}

}
