
package com.aryn.cloud.common.job.config;

import com.aryn.cloud.common.job.properties.XxlJobProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * xxlJob调度任务配置
 *
 * @author 雨滴kian
 * @date 2022/6/2
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfig {

	@Bean
	public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties xxlJobProperties, Environment environment) {
		XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
		xxlJobSpringExecutor.setAdminAddresses(xxlJobProperties.getAdminAddresses());
		String appName = xxlJobProperties.getAppName();
		if (!StringUtils.hasText(appName)) {
			appName = environment.getProperty("spring.application.name");
		}
		xxlJobSpringExecutor.setAppname(appName);
		xxlJobSpringExecutor.setIp(xxlJobProperties.getIp());
		xxlJobSpringExecutor.setPort(xxlJobProperties.getPort());
		xxlJobSpringExecutor.setAccessToken(xxlJobProperties.getAccessToken());
		xxlJobSpringExecutor.setLogPath(xxlJobProperties.getLogPath());
		xxlJobSpringExecutor.setLogRetentionDays(xxlJobProperties.getLogRetentionDays());
		return xxlJobSpringExecutor;
	}

}
