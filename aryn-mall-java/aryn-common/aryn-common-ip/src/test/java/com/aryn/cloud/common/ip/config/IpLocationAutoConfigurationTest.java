package com.aryn.cloud.common.ip.config;

import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.ip.job.IpRegionDataRefreshJobHandler;
import com.aryn.cloud.common.ip.provider.Ip2RegionLocationProvider;
import com.aryn.cloud.common.ip.updater.IpRegionDataUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动配置：默认启用并注册到 IpUtils；关闭时完全不接管（沿用旧 HTTP 实现）；
 * 引入 xxl-job 且允许热更新时注册刷新任务 handler。
 */
class IpLocationAutoConfigurationTest {

	@TempDir
	Path tempDir;

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(
				AutoConfigurations.of(IpLocationAutoConfiguration.class, IpRegionRefreshJobAutoConfiguration.class));

	@AfterEach
	void tearDown() {
		IpUtils.resetAddressResolver();
	}

	@Test
	void shouldRegisterProviderAndResolverByDefault() {
		runner.withPropertyValues(baseProperties("false"))
			.run(context -> {
				assertThat(context).hasSingleBean(Ip2RegionLocationProvider.class);
				assertThat(context).hasSingleBean(IpRegionDataUpdater.class);
				assertThat(IpUtils.hasCustomAddressResolver()).isTrue();
				assertThat(IpUtils.getWhoisAddress("223.5.5.5")).contains("浙江");
				assertThat(context.getBeansOfType(IpRegionDataRefreshJobHandler.class))
					.as("热更新开关关闭时不注册任务")
					.isEmpty();
			});
	}

	@Test
	void shouldRegisterRefreshJobHandlerWhenXxlJobPresent() {
		runner.withPropertyValues(baseProperties("true"))
			.run(context -> assertThat(context).hasSingleBean(IpRegionDataRefreshJobHandler.class));
	}

	@Test
	void shouldNotTakeOverWhenDisabled() {
		runner.withPropertyValues("aryn.ip.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean(Ip2RegionLocationProvider.class);
				assertThat(context).doesNotHaveBean(IpRegionDataRefreshJobHandler.class);
				assertThat(IpUtils.hasCustomAddressResolver()).isFalse();
			});
	}

	@Test
	void shouldOnlyKeepHttpFallbackWhenIp2RegionSwitchOff() {
		runner.withPropertyValues(baseProperties("false", "aryn.ip.ip2region.enabled=false"))
			.run(context -> {
				Ip2RegionLocationProvider provider = context.getBean(Ip2RegionLocationProvider.class);
				assertThat(provider.isAvailable()).isFalse();
				assertThat(IpUtils.hasCustomAddressResolver()).as("离线库关闭时不接管 IpUtils").isFalse();
			});
	}

	private String[] baseProperties(String refreshEnabled, String... extra) {
		java.util.List<String> properties = new java.util.ArrayList<>(java.util.List.of("aryn.ip.enabled=true",
				"aryn.ip.fallback-http=false", "aryn.ip.ip2region.data-dir=" + tempDir,
				"aryn.ip.refresh.enabled=" + refreshEnabled));
		properties.addAll(java.util.List.of(extra));
		return properties.toArray(String[]::new);
	}

}
