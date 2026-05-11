
package com.aryn.cloud.common.seata.config;

import com.aryn.cloud.common.core.factory.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/25
 */
@Configuration(proxyBeanMethods = false)
@PropertySource(value = "classpath:seata-config.yml", factory = YamlPropertySourceFactory.class)
@ConditionalOnProperty(prefix = "hx", value = "cloud.enable", matchIfMissing = true)
public class ArynSeataConfiguration {

}
