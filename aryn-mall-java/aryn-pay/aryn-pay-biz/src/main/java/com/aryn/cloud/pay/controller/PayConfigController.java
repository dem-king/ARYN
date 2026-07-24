
package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.desensitization.KeyDesensitization;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.service.IPayConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 支付配置
 *
 * @author 雨滴kian
 * @since 2022/3/18 10:11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payconfig")
@Tag(description = "payconfig", name = "支付配置")
public class PayConfigController {

	private static final long MAX_CERT_FILE_SIZE = 1024 * 1024;

	private static final Set<String> ALLOWED_CERT_EXTENSIONS = Set.of("pem", "crt", "cer", "p12");

	private final IPayConfigService payConfigService;

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	@Value("${cert-dir:}")
	private String certDir;

	@Operation(summary = "支付配置列表")
	@SaCheckPermission("pay:payconfig:page")
	@GetMapping("/page")
	public Result page(Page page, PayConfig payConfig) {
		return Result.success(payConfigService.page(page, Wrappers.lambdaQuery(payConfig)));
	}

	@Operation(summary = "支付配置查询")
	@SaCheckPermission("pay:payconfig:get")
	@GetMapping("/{id}")
	public Result page(@PathVariable("id") String id) {
		return Result.success(payConfigService.getById(id));
	}

	@Operation(summary = "支付配置新增")
	@SaCheckPermission("pay:payconfig:add")
	@PostMapping
	public Result add(@RequestBody @Valid PayConfig payConfig) {
		clearManagedFields(payConfig);
		return Result.success(payConfigService.saveConfig(payConfig));
	}

	@Operation(summary = "支付配置编辑")
	@SaCheckPermission("pay:payconfig:edit")
	@PutMapping
	public Result edit(@RequestBody PayConfig payConfig) {
		if (StrUtil.isBlank(payConfig.getId())) {
			return Result.fail("支付配置ID为空");
		}
		PayConfig target = payConfigService.getById(payConfig.getId());
		if (ObjectUtil.isNull(target)) {
			return Result.fail("支付不存在");
		}
		clearManagedFields(payConfig);

		if (StringUtils.hasText(target.getApiv3Key())
				&& keyDesensitization.serialize(target.getApiv3Key()).equals(payConfig.getApiv3Key())) {
			payConfig.setApiv3Key(null);
		}
		if (StringUtils.hasText(target.getCertSerialNo())
				&& keyDesensitization.serialize(target.getCertSerialNo()).equals(payConfig.getCertSerialNo())) {
			payConfig.setCertSerialNo(null);
		}
		if (StringUtils.hasText(target.getMchKey())
				&& keyDesensitization.serialize(target.getMchKey()).equals(payConfig.getMchKey())) {
			payConfig.setMchKey(null);
		}
		return Result.success(payConfigService.updateById(payConfig));
	}

	@Operation(summary = "支付配置删除")
	@SaCheckPermission("pay:payconfig:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		return Result.success(payConfigService.removeById(id));
	}

	@Operation(summary = "上传证书")
	@SaCheckPermission("pay:payconfig:edit")
	@PostMapping("/cert/upload")
	public Result uploadFile(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return Result.fail("证书文件不能为空");
		}
		if (file.getSize() > MAX_CERT_FILE_SIZE) {
			return Result.fail("证书文件不能超过1MB");
		}
		if (!StringUtils.hasText(certDir)) {
			return Result.fail("证书目录未配置");
		}
		String extension = getExtension(file.getOriginalFilename());
		if (!ALLOWED_CERT_EXTENSIONS.contains(extension)) {
			return Result.fail("仅支持 pem、crt、cer、p12 证书文件");
		}

		Path certificateDirectory = Paths.get(certDir).toAbsolutePath().normalize();
		Files.createDirectories(certificateDirectory);
		Path target = certificateDirectory.resolve(UUID.randomUUID() + "." + extension).normalize();
		if (!target.startsWith(certificateDirectory)) {
			return Result.fail("证书文件路径非法");
		}
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, target);
		}
		return Result.success(target.toString());
	}

	private String getExtension(String filename) {
		if (!StringUtils.hasText(filename)) {
			return StrUtil.EMPTY;
		}
		int separator = filename.lastIndexOf('.');
		if (separator < 0 || separator == filename.length() - 1) {
			return StrUtil.EMPTY;
		}
		return filename.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private void clearManagedFields(PayConfig payConfig) {
		payConfig.setTenantId(null);
		payConfig.setDelFlag(null);
		payConfig.setCreateBy(null);
		payConfig.setCreateTime(null);
		payConfig.setUpdateBy(null);
		payConfig.setUpdateTime(null);
	}

}
