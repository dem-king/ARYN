/*
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.common.search;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Meilisearch 自动配置类
 * <p>
 * 当 classpath 中存在 {@link Client} 时自动激活，注册 Meilisearch Client 和 Template Bean。
 * </p>
 *
 * @author aryn
 */
@Configuration
@ConditionalOnClass(Client.class)
@EnableConfigurationProperties(MeilisearchProperties.class)
public class MeilisearchAutoConfiguration {

	/**
	 * 注册 Meilisearch Client Bean
	 * @param properties Meilisearch 配置属性
	 * @return Meilisearch Client 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public Client meilisearchClient(MeilisearchProperties properties) {
		Config config = new Config(properties.getHost(), properties.getApiKey());
		return new Client(config);
	}

	/**
	 * 注册 MeilisearchTemplate Bean
	 * @param client Meilisearch Client 实例
	 * @return MeilisearchTemplate 实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public MeilisearchTemplate meilisearchTemplate(Client client) {
		return new MeilisearchTemplate(client);
	}

}