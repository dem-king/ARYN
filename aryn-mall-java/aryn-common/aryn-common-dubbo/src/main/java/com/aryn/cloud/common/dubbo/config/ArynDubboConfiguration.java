
package com.aryn.cloud.common.dubbo.config;

import com.aryn.cloud.common.core.factory.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * dubbo配置
 * @author 雨滴kian
 * @date 2024/11/25
 */
@PropertySource(value = "classpath:dubbo-config.yml", factory = YamlPropertySourceFactory.class)
@ConditionalOnProperty(prefix = "hx", value = "cloud.enable", matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
public class ArynDubboConfiguration {

}
