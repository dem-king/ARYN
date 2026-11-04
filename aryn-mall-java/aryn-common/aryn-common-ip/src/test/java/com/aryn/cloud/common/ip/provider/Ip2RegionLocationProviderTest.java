package com.aryn.cloud.common.ip.provider;

import com.aryn.cloud.common.core.util.IpLocationResolver;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.ip.config.IpLocationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ip2region 离线解析器：classpath 加载、真实查询、外部文件热重载、兜底链路与降级。
 *
 * <p>用模块内置的 v4 xdb 做真实查询（不联网），断言只校验省份包含关系，避免数据版本升级导致
 * 用例脆弱；IP 归属以 ip2region 自带数据为准。</p>
 */
class Ip2RegionLocationProviderTest {

	private static final String XDB_CLASSPATH = "classpath:ip2region/ip2region_v4.xdb";

	@TempDir
	Path tempDir;

	private Ip2RegionLocationProvider provider;

	@AfterEach
	void tearDown() {
		if (provider != null) {
			provider.close();
			provider = null;
		}
		IpUtils.resetAddressResolver();
	}

	@Test
	void shouldResolveIpWithBundledClasspathXdb() {
		provider = new Ip2RegionLocationProvider(properties(false));
		provider.init();

		assertThat(provider.isAvailable()).isTrue();
		assertThat(provider.sourceDescription()).contains("classpath:");
		assertThat(provider.dataBuiltAtEpochSeconds()).as("xdb header 构建时间").isPositive();
		assertThat(provider.resolve("223.5.5.5")).contains("浙江");
		assertThat(provider.resolve("114.114.114.114")).contains("江苏");
		assertThat(provider.resolve("192.168.1.1")).isEqualTo(IpUtils.INTRANET);
		assertThat(provider.resolve(" ")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(provider.resolve("not-an-ip")).isEqualTo(IpUtils.UNKNOWN);
		assertThat(provider.resolve("240e:3b7:3272:d8d0:db09:c067:8d59:539e")).as("v4 库不支持 IPv6").isEqualTo(IpUtils.UNKNOWN);
		assertThat(IpUtils.getWhoisAddress("223.5.5.5")).as("IpUtils 入口已切换到离线实现").contains("浙江");
	}

	@Test
	void shouldReloadFromExternalFileAndSwitchSource() throws Exception {
		provider = new Ip2RegionLocationProvider(properties(false));
		provider.init();
		Path xdbFile = tempDir.resolve("ip2region_v4.xdb");
		try (InputStream inputStream = new ClassPathResource("ip2region/ip2region_v4.xdb").getInputStream()) {
			Files.copy(inputStream, xdbFile, StandardCopyOption.REPLACE_EXISTING);
		}

		provider.reload(xdbFile);

		assertThat(provider.sourceDescription()).contains(xdbFile.toAbsolutePath().toString());
		assertThat(provider.resolve("223.5.5.5")).contains("浙江");
	}

	@Test
	void shouldFallbackToSecondaryResolverWhenIp2RegionMisses() {
		IpLocationResolver fallback = ip -> "美国";
		provider = new Ip2RegionLocationProvider(properties(false), fallback);
		provider.init();

		assertThat(provider.resolve("240e:3b7:3272:d8d0:db09:c067:8d59:539e")).isEqualTo("美国");
	}

	@Test
	void shouldNotRegisterWhenIp2RegionDisabled() {
		IpLocationProperties properties = properties(false);
		properties.getIp2region().setEnabled(false);
		provider = new Ip2RegionLocationProvider(properties);
		provider.init();

		assertThat(provider.isAvailable()).isFalse();
		assertThat(IpUtils.hasCustomAddressResolver()).as("未启用时不接管 IpUtils").isFalse();
	}

	@Test
	void shouldFallbackToClasspathWhenExternalFileInvalid() throws Exception {
		Path invalid = tempDir.resolve("ip2region_v4.xdb");
		Files.write(invalid, "not-a-xdb".getBytes());
		IpLocationProperties properties = properties(false);
		properties.getIp2region().setDataDir(tempDir.toString());
		provider = new Ip2RegionLocationProvider(properties);
		provider.init();

		assertThat(provider.isAvailable()).isTrue();
		assertThat(provider.sourceDescription()).contains("classpath:");
	}

	private IpLocationProperties properties(boolean fallbackHttp) {
		IpLocationProperties properties = new IpLocationProperties();
		properties.setFallbackHttp(fallbackHttp);
		properties.getIp2region().setDataDir(tempDir.toString());
		properties.getRefresh().setSampleIps(java.util.List.of("223.5.5.5"));
		return properties;
	}

}
