
package com.aryn.cloud.common.core.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 回调前缀配置类
 *
 * @author 雨滴kian
 * @date 2025/6/18
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "callback.prefix")
public class CallbackPrefixProperties {

	/**
	 * 支付回调前缀
	 */
	private String pay;

	/**
	 * 物流回调前缀
	 */
	private String logistics;

}
