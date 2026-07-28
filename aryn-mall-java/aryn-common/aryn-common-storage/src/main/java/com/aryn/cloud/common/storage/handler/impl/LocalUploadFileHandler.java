/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler.impl;

import cn.hutool.core.io.IoUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.storage.entity.StorageConfig;
import com.aryn.cloud.common.storage.entity.StoredObject;
import lombok.SneakyThrows;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 本地上传文件处理器
 *
 * @author 雨滴kian
 */
@Component
@RequiredArgsConstructor
public class LocalUploadFileHandler extends AbstractUploadFileHandler {

	private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[0-9]{1,32}$");

	private final Environment environment;

	@SneakyThrows
	@Override
	public StoredObject doUploadObject(StorageConfig storageConfig, InputStream inputStream, String fileName,
			long size) {
		String rootPath = storageConfig.getBucket();
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(rootPath) || !StringUtils.hasText(tenantId)
				|| !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
			throw new IllegalArgumentException("本地文件存储配置不正确");
		}

		// 获取后缀
		String suffix = "";
		if (fileName.contains(".")) {
			suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
		}
		String uuidFileName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");

		Path root = Path.of(rootPath).toAbsolutePath().normalize();
		Path tenantDirectory = root.resolve(tenantId).normalize();
		Path destination = tenantDirectory.resolve(uuidFileName).normalize();
		if (!destination.startsWith(root)) {
			throw new IllegalArgumentException("本地文件存储路径不正确");
		}
		Files.createDirectories(tenantDirectory);
		try (FileOutputStream fos = new FileOutputStream(destination.toFile())) {
			IoUtil.copy(inputStream, fos);
		}

		String objectKey = tenantId + "/" + uuidFileName;
		String url = resolvePublicBaseUrl(storageConfig.getDomain()) + "/file/local/" + objectKey;
		return new StoredObject(objectKey, url);
	}

	String resolvePublicBaseUrl(String domain) {
		if (!StringUtils.hasText(domain)) {
			Boolean cloudEnabled = environment.getProperty("hx.cloud.enable", Boolean.class, true);
			return Boolean.FALSE.equals(cloudEnabled) ? "/boot" : "/upms";
		}
		String normalizedDomain = domain.trim();
		if (!normalizedDomain.startsWith("http://") && !normalizedDomain.startsWith("https://")) {
			normalizedDomain = "https://" + normalizedDomain;
		}
		return normalizedDomain.endsWith("/") ? normalizedDomain.substring(0, normalizedDomain.length() - 1)
				: normalizedDomain;
	}

	@Override
	public String getType() {
		return "local";
	}

}
