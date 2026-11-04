package com.aryn.cloud.common.ip.config;

import com.aryn.cloud.common.ip.provider.Ip2RegionLocationProvider;
import com.aryn.cloud.common.ip.updater.IpRegionDataUpdater;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * IP 归属地自动配置。
 *
 * <p>提供 ip2region 离线解析器并注册到 {@code IpUtils}；未引入本模块的服务继续使用
 * 第三方 HTTP 实现，行为不变。可通过 {@code aryn.ip.enabled=false} 完全关闭。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "aryn.ip", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IpLocationProperties.class)
public class IpLocationAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Ip2RegionLocationProvider ip2RegionLocationProvider(IpLocationProperties properties) {
		// init() 由 @PostConstruct 触发，加载失败会在 provider 内部降级并打日志
		return new Ip2RegionLocationProvider(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public IpRegionDataUpdater ipRegionDataUpdater(IpLocationProperties properties,
			Ip2RegionLocationProvider ip2RegionLocationProvider) {
		return new IpRegionDataUpdater(properties, ip2RegionLocationProvider);
	}

}
