package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class LocalFilePathResolver {

	private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[0-9]{1,32}$");

	private static final Pattern GENERATED_FILE_PATTERN = Pattern
		.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?i:jpg|jpeg|png|gif|bmp|webp)$");

	public Path resolve(String rootPath, String tenantId, String fileName) {
		if (!StringUtils.hasText(tenantId) || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
			throw new ArynBusinessException("非法租户标识");
		}
		if (!StringUtils.hasText(fileName) || !GENERATED_FILE_PATTERN.matcher(fileName).matches()) {
			throw new ArynBusinessException("非法文件路径");
		}
		if (!StringUtils.hasText(rootPath)) {
			throw new ArynBusinessException("本地存储根路径未配置");
		}

		Path root = Path.of(rootPath).toAbsolutePath().normalize();
		Path target = root.resolve(tenantId).resolve(fileName).normalize();
		if (!target.startsWith(root)) {
			throw new ArynBusinessException("非法文件路径");
		}
		if (!Files.exists(target)) {
			return target;
		}
		try {
			Path realRoot = root.toRealPath();
			Path realTarget = target.toRealPath();
			if (!realTarget.startsWith(realRoot)) {
				throw new ArynBusinessException("非法文件路径");
			}
			return realTarget;
		}
		catch (IOException exception) {
			throw new ArynBusinessException("文件路径解析失败");
		}
	}

}
