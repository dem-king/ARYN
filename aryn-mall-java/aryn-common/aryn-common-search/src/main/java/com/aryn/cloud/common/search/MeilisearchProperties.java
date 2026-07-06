/*
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.common.search;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Meilisearch 配置属性
 *
 * @author aryn
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "meilisearch")
public class MeilisearchProperties {

	/**
	 * Meilisearch 服务地址
	 */
	private String host = "http://localhost:7700";

	/**
	 * Meilisearch Master Key，用于鉴权
	 */
	private String apiKey;

}