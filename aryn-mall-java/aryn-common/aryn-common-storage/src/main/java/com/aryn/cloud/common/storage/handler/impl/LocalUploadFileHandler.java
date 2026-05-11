/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.common.storage.handler.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.storage.entity.StorageConfig;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.UUID;

/**
 * 本地上传文件处理器
 *
 * @author 雨滴kian
 */
@Component
public class LocalUploadFileHandler extends AbstractUploadFileHandler {

	@Autowired
	private Environment environment;

	public String getActiveProfile() {
		String[] profiles = environment.getActiveProfiles();
		if (profiles.length > 0) {
			return profiles[0];
		}
		return "default";
	}

	public int getServerPort() {
		return Integer.parseInt(environment.getProperty("server.port"));
	}

	@SneakyThrows
	@Override
	public String doUploadFile(StorageConfig storageConfig, InputStream inputStream, String fileName, long size) {
		// 对于本地存储：
		// endpoint 作为访问域名 (例如 https://file.example.com)
		// bucket 作为本地存储根路径 (例如 /data/files)

		String domain = storageConfig.getDomain();
		String rootPath = storageConfig.getBucket();
		String prefix = "upms";
		if (getActiveProfile().equals("dev")) {
			prefix = "boot";
		}
		// 如果 domain 为空，尝试自动获取
		if (!StringUtils.hasText(domain)) {
			try {
				InetAddress inet = InetAddress.getLocalHost();
				domain = "http://" + inet.getHostAddress() + ":" + getServerPort() + "/" + prefix;
			}
			catch (Exception ex) {
				domain = "http://localhost:9900" + "/" + prefix;
			}
		}

		// 自动补全协议头
		if (!domain.startsWith("http")) {
			domain = "https://" + domain;
		}
		if (domain.endsWith("/")) {
			domain = domain.substring(0, domain.length() - 1);
		}

		// 获取后缀
		String suffix = "";
		if (fileName.contains(".")) {
			suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
		}
		String uuidFileName = UUID.randomUUID() + (StringUtils.hasText(suffix) ? "." + suffix : "");

		// 相对路径： tenantId / dir / uuidFileName
		String relativePath = ArynTenantContextHolder.getTenantId() + "/" + uuidFileName;

		// 绝对路径
		String absolutePath = rootPath + "/" + relativePath;

		// 确保目录存在
		File destFile = new File(absolutePath);
		FileUtil.touch(destFile);

		// 写入文件
		try (FileOutputStream fos = new FileOutputStream(destFile)) {
			IoUtil.copy(inputStream, fos);
		}

		// 返回访问 URL
		return domain + "/file/local/" + relativePath;
	}

	@Override
	public String getType() {
		return "local";
	}

}
