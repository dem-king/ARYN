package com.aryn.cloud.common.ip.config;

import com.aryn.cloud.common.ip.job.IpRegionDataRefreshJobHandler;
import com.aryn.cloud.common.ip.updater.IpRegionDataUpdater;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * ip2region 数据刷新任务自动配置。
 *
 * <p>仅在服务引入了 xxl-job（{@code aryn-common-job}）且启用了热更新时注册 handler；
 * 没有调度器的服务（如部分纯 RPC 服务）不会加载本配置。</p>
 *
 * <p>注意：不要在此加 {@code @ConditionalOnBean(IpRegionDataUpdater.class)}——本模块的配置类
 * 同时会被 {@code com.aryn.cloud} 包扫描和自动配置导入，排序不确定；用方法参数依赖注入即可，
 * 缺少 updater 时由上游配置开关保证不会进入本配置。</p>
 */
@AutoConfiguration(after = IpLocationAutoConfiguration.class)
@ConditionalOnClass(name = "com.xxl.job.core.handler.annotation.XxlJob")
@ConditionalOnProperty(prefix = "aryn.ip", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "aryn.ip.refresh", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IpRegionRefreshJobAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public IpRegionDataRefreshJobHandler ipRegionDataRefreshJobHandler(IpRegionDataUpdater updater) {
		return new IpRegionDataRefreshJobHandler(updater);
	}

}
