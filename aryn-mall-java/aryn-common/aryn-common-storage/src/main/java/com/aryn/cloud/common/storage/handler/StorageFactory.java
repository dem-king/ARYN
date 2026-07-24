/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler;

import org.springframework.stereotype.Component;

import com.aryn.cloud.common.core.constant.StorageTypeConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 雨滴kian
 */
@Component
public class StorageFactory {

	private final Map<String, ArynUploadFileHandler> strategyMap = new HashMap<>();

	/**
	 * 构造方法中注入所有策略实现类
	 * @param strategies Spring 会自动注入所有实现 SocialLoginStrategy 的 Bean
	 */
	public StorageFactory(List<ArynUploadFileHandler> strategies) {
		for (ArynUploadFileHandler strategy : strategies) {
			strategyMap.put(strategy.getType(), strategy);
			if (StorageTypeConstants.LEGACY_OSS.equals(strategy.getType())) {
				StorageTypeConstants.OBJECT_STORAGE_TYPES.forEach(type -> strategyMap.put(type, strategy));
				StorageTypeConstants.LEGACY_TYPES.keySet().forEach(type -> strategyMap.put(type, strategy));
			}
		}
	}

	/**
	 * 根据类型获取对应的策略
	 * @param type 存储类型
	 * @return 对应的策略实现
	 */
	public ArynUploadFileHandler getStrategy(String type) {
		ArynUploadFileHandler strategy = strategyMap.get(StorageTypeConstants.normalize(type));
		if (strategy == null) {
			throw new IllegalArgumentException("不支持的文件存储类型: " + type);
		}
		return strategy;
	}

}
