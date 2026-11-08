
package com.aryn.cloud.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付材料主密钥配置。只允许通过部署环境变量/Secret 注入，禁止写入代码、SQL 或 Nacos 明文。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aryn.pay.credential")
public class PayCredentialProperties {

	/**
	 * 当前加密主密钥，Base64 编码的 32 字节 AES-256 密钥。
	 */
	private String masterKey;

	/**
	 * 当前主密钥标识，写入每条材料，解密时按此定位。
	 */
	private String keyId = "primary";

	/**
	 * 历史主密钥，格式 keyId=Base64,keyId=Base64，用于轮换后解密旧材料。
	 */
	private String previousKeys;

	/**
	 * 单份材料明文上限，默认 256KB。
	 */
	private int maxMaterialBytes = 256 * 1024;

}
