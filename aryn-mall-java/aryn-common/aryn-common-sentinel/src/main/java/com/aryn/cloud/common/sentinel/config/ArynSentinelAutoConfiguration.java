
package com.aryn.cloud.common.sentinel.config;

import com.alibaba.cloud.sentinel.custom.SentinelAutoConfiguration;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.aryn.cloud.common.sentinel.handler.ArynUrlBlockHandler;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * sentinel 配置类
 *
 * @author 雨滴kian
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(SentinelAutoConfiguration.class)
public class ArynSentinelAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public BlockExceptionHandler blockExceptionHandler() {
		return new ArynUrlBlockHandler();
	}

}
