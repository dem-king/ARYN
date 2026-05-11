/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler.impl;

import com.aryn.cloud.common.storage.entity.StorageConfig;
import com.aryn.cloud.common.storage.handler.ArynUploadFileHandler;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;

import java.io.InputStream;

/**
 * @author 雨滴kian
 */
public abstract class AbstractUploadFileHandler implements ArynUploadFileHandler {

	@Override
	public String uploadFile(SysStorageConfigDTO sysStorageConfig, InputStream inputStream, String fileName,
			String contextType, long size) {
		StorageConfig storageConfig = validateRequest(sysStorageConfig, contextType);
		return doUploadFile(storageConfig, inputStream, fileName, size);
	}

	public abstract String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName,
			long size);

	private StorageConfig validateRequest(SysStorageConfigDTO sysStorageConfig, String contextType) {
		StorageConfig storageConfig = new StorageConfig();
		storageConfig.setAccessKeyId(sysStorageConfig.getAccessKey());
		storageConfig.setAccessKeySecret(sysStorageConfig.getAccessSecret());
		storageConfig.setBucket(sysStorageConfig.getBucket());
		storageConfig.setEndpoint(sysStorageConfig.getEndpoint());
		storageConfig.setDir(sysStorageConfig.getDir());
		storageConfig.setContextType(contextType);
		storageConfig.setDomain(sysStorageConfig.getDomain());
		storageConfig.setStyleAccessEnabled(sysStorageConfig.getStyleAccessEnabled());
		return storageConfig;
	}

}
