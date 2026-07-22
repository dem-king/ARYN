
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.service.ISysMaterialService;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import com.aryn.cloud.upms.support.LocalFilePathResolver;
import com.aryn.cloud.upms.support.UploadFileValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件上传
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:45
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/file")
@Tag(description = "file", name = "文件上传")
public class SysUploadController {

	private final ISysStorageConfigService sysStorageConfigService;

	private final StorageFactory storageFactory;

	private final ISysMaterialService sysMaterialService;

	private final UploadFileValidator uploadFileValidator;

	private final LocalFilePathResolver localFilePathResolver;

	@Operation(summary = "文件上传")
	@SaCheckPermission("upms:material:add")
	@PostMapping("/upload")
	public Result upload(@RequestPart("file") MultipartFile file,
			@RequestParam(value = "groupId", required = false) String groupId,
			@RequestParam(value = "type", required = false) String type) throws Exception {
		uploadFileValidator.validate(file);
		SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
		if (ObjectUtil.isNull(sysStorageConfig)) {
			throw new ArynBusinessException("文件存储配置为空");
		}
		String url = storageFactory.getStrategy(sysStorageConfig.getType())
			.uploadFile(sysStorageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
					file.getSize());

		SysMaterial sysMaterial = new SysMaterial();
		sysMaterial.setUrl(url);
		sysMaterial.setFileSize(file.getSize());
		sysMaterial.setGroupId(groupId);
		sysMaterial.setType(type);
		sysMaterial.setName(file.getOriginalFilename());
		return Result.success(sysMaterialService.save(sysMaterial));
	}

	@Operation(summary = "移动端文件上传")
	@PostMapping("/app/upload")
	public Result appUpload(@RequestPart("file") MultipartFile file) throws Exception {
		uploadFileValidator.validate(file);
		SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
		if (ObjectUtil.isNull(sysStorageConfig)) {
			throw new ArynBusinessException("文件存储配置为空");
		}
		String url = storageFactory.getStrategy(sysStorageConfig.getType())
			.uploadFile(sysStorageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
					file.getSize());
		return Result.success(url);
	}

	@Operation(summary = "本地文件预览/下载")
	@GetMapping("/local/{tenantId}/{url}")
	public void getLocalFile(@PathVariable String tenantId, @PathVariable String url, HttpServletResponse response) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			SysStorageConfigDTO sysStorageConfig = sysStorageConfigService.getConfig();
			if (ObjectUtil.isNull(sysStorageConfig)) {
				throw new ArynBusinessException("文件存储配置为空");
			}

			Path file = localFilePathResolver.resolve(sysStorageConfig.getBucket(), tenantId, url);
			if (!Files.isRegularFile(file)) {
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			response.setContentType(FileUtil.getMimeType(file.getFileName().toString()));
			try (FileInputStream in = new FileInputStream(file.toFile())) {
				IoUtil.copy(in, response.getOutputStream());
			}
			catch (IOException exception) {
				log.error("文件读取失败", exception);
			}
		}
		finally {
			if (StrUtil.isBlank(previousTenantId)) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
		}
	}

}
