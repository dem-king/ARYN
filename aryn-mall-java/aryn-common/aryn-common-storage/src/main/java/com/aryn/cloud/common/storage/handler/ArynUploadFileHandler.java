
package com.aryn.cloud.common.storage.handler;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.storage.entity.StoredObject;

import java.io.File;
import java.io.InputStream;

public interface ArynUploadFileHandler {

	/**
	 * 上传文件
	 * @param sysStorageConfig 存储配置
	 * @param inputStream 文件流
	 * @param fileName 文件名
	 * @param contextType 文件类型
	 * @param size 文件大小
	 * @return 文件访问URL
	 */
	String uploadFile(SysStorageConfigDTO sysStorageConfig, InputStream inputStream, String fileName,
			String contextType, long size);

	default StoredObject uploadObject(SysStorageConfigDTO sysStorageConfig, InputStream inputStream, String fileName,
			String contextType, long size) {
		return new StoredObject(null, uploadFile(sysStorageConfig, inputStream, fileName, contextType, size));
	}

	/**
	 * 返回该策略支持的文件上传类型
	 * @return 登录类型标识（如 "local" 表示本地，"oss" ）
	 */
	String getType();

}
