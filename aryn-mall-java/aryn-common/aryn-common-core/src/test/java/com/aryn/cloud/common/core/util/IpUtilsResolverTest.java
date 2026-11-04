package com.aryn.cloud.common.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IpUtils} 归属地 SPI 注册与降级行为：
 * - 注册后统一走自定义解析器（ip2region 离线库），调用方无需改动；
 * - 空/内网/非法 IP 在入口短路，不调用解析器；
 * - 解析器异常不能让登录链路失败，统一降级为「未知」。
 */
class IpUtilsResolverTest {

	private final AtomicReference<String> resolvedIp = new AtomicReference<>();

	private final IpLocationResolver stubResolver = ip -> {
		resolvedIp.set(ip);
		return "浙江省 杭州市";
	};

	@AfterEach
	void tearDown() {
		IpUtils.resetAddressResolver();
		resolvedIp.set(null);
	}

	@Test
	void shouldDelegateToRegisteredResolver() {
		IpUtils.registerAddressResolver(stubResolver);

		assertThat(IpUtils.hasCustomAddressResolver()).isTrue();
		assertThat(IpUtils.getWhoisAddress(" 223.5.5.5 ")).isEqualTo("浙江省 杭州市");
		assertThat(resolvedIp.get()).isEqualTo("223.5.5.5");
	}

	@Test
	void shouldShortCircuitBlankPrivateAndInvalidIp() {
		IpUtils.registerAddressResolver(stubResolver);

		assertThat(IpUtils.getWhoisAddress(" ")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpUtils.getWhoisAddress("192.168.1.2")).isEqualTo(IpUtils.INTRANET);
		assertThat(IpUtils.getWhoisAddress("10.0.0.1")).isEqualTo(IpUtils.INTRANET);
		assertThat(IpUtils.getWhoisAddress("not-an-ip")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(resolvedIp.get()).as("空白/内网/非法 IP 不应调用解析器").isNull();
	}

	@Test
	void shouldFallbackToUnknownWhenResolverThrows() {
		IpUtils.registerAddressResolver(ip -> {
			throw new IllegalStateException("resolver down");
		});

		assertThat(IpUtils.getWhoisAddress("223.5.5.5")).isEqualTo(IpUtils.UNKNOWN);
	}

	@Test
	void shouldResetToDefaultResolver() {
		IpUtils.registerAddressResolver(stubResolver);
		IpUtils.resetAddressResolver();
		assertThat(IpUtils.hasCustomAddressResolver()).isFalse();

		IpUtils.registerAddressResolver(null);
		assertThat(IpUtils.hasCustomAddressResolver()).isFalse();
	}

}
