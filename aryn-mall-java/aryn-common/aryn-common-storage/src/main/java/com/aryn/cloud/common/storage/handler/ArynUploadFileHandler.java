
package com.aryn.cloud.common.storage.handler;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;

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

	/**
	 * 上传私有文件（配送凭证等敏感材料）。
	 *
	 * <p>默认与普通上传一致，保证既有存储策略可编译；本地存储会落到 private/ 子目录，
	 * 由下载端点强制校验签名。对象存储的私有性仍依赖桶 ACL/私有读策略。
	 * @return 文件访问URL
	 */
	default String uploadPrivateFile(SysStorageConfigDTO sysStorageConfig, InputStream inputStream, String fileName,
			String contextType, long size) {
		return uploadFile(sysStorageConfig, inputStream, fileName, contextType, size);
	}

	/**
	 * 返回该策略支持的文件上传类型
	 * @return 登录类型标识（如 "local" 表示本地，"oss" ）
	 */
	String getType();

}
