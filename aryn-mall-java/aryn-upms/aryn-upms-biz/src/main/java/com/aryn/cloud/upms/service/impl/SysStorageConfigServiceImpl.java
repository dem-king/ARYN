
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.StorageTypeConstants;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.core.desensitization.KeyDesensitization;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysStorageConfig;
import com.aryn.cloud.upms.mapper.SysStorageConfigMapper;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 文件存储配置
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@RequiredArgsConstructor
public class SysStorageConfigServiceImpl extends ServiceImpl<SysStorageConfigMapper, SysStorageConfig>
		implements ISysStorageConfigService {

	private static final Set<String> VALID_STATUSES = Set.of(CommonConstants.NORMAL_STATUS, CommonConstants.YES);

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	@Override
	public SysStorageConfigDTO getConfig() {
		return baseMapper.selectConfig();
	}

	@Override
	public List<String> getLocalStorageRoots() {
		return this.list(Wrappers.<SysStorageConfig>lambdaQuery()
			.select(SysStorageConfig::getBucket)
			.eq(SysStorageConfig::getType, StorageTypeConstants.LOCAL))
			.stream()
			.map(SysStorageConfig::getBucket)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveStorageConfig(SysStorageConfig sysStorageConfig) {
		normalizeAndValidate(sysStorageConfig);
		if (CommonConstants.NORMAL_STATUS.equals(sysStorageConfig.getStatus())) {
			updateStatus();
		}
		return this.save(sysStorageConfig);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateStorageConfigById(SysStorageConfig sysStorageConfig) {
		SysStorageConfig target = this.getById(sysStorageConfig.getId());
		if (Objects.isNull(target)) {
			throw new ArynBusinessException("文件配置不存在");
		}

		restoreMaskedSecret(target.getAccessKey(), sysStorageConfig.getAccessKey(), sysStorageConfig::setAccessKey);
		restoreMaskedSecret(target.getAccessSecret(), sysStorageConfig.getAccessSecret(),
				sysStorageConfig::setAccessSecret);
		normalizeAndValidate(sysStorageConfig);
		if (CommonConstants.NORMAL_STATUS.equals(sysStorageConfig.getStatus())) {
			updateStatus();
		}

		boolean updated = this.updateById(sysStorageConfig);
		if (updated && StorageTypeConstants.LOCAL.equals(sysStorageConfig.getType())) {
			clearObjectStorageCredentials(sysStorageConfig.getId());
		}
		return updated;
	}

	public void updateStatus() {
		SysStorageConfig sysStorageConfig = new SysStorageConfig();
		sysStorageConfig.setStatus(CommonConstants.YES);
		super.update(sysStorageConfig, Wrappers.<SysStorageConfig>lambdaQuery()
			.eq(SysStorageConfig::getStatus, CommonConstants.NORMAL_STATUS));
	}

	private void normalizeAndValidate(SysStorageConfig config) {
		String type = StorageTypeConstants.normalize(config.getType());
		if (StorageTypeConstants.LEGACY_OSS.equals(type)) {
			type = CommonConstants.YES.equals(config.getStyleAccessEnabled()) ? StorageTypeConstants.MINIO
					: StorageTypeConstants.ALIYUN;
		}
		if (!StorageTypeConstants.isSupported(type)) {
			throw new ArynBusinessException("不支持的文件存储类型");
		}
		config.setType(type);
		config.setStatus(trimToNull(config.getStatus()));
		config.setBucket(trimToNull(config.getBucket()));
		config.setDir(trimToNull(config.getDir()));
		config.setDomain(trimToNull(config.getDomain()));
		config.setEndpoint(trimToNull(config.getEndpoint()));

		if (!VALID_STATUSES.contains(config.getStatus())) {
			throw new ArynBusinessException("文件存储状态不正确");
		}
		if (!StringUtils.hasText(config.getBucket())) {
			throw new ArynBusinessException(StorageTypeConstants.LOCAL.equals(type) ? "本地存储根目录不能为空" : "Bucket不能为空");
		}
		validateDomain(config.getDomain());

		if (StorageTypeConstants.LOCAL.equals(type)) {
			validateLocalRoot(config.getBucket());
			config.setAccessKey(null);
			config.setAccessSecret(null);
			config.setEndpoint(null);
			config.setStyleAccessEnabled(CommonConstants.NO);
			return;
		}

		validateBucket(config.getBucket());
		validateObjectDirectory(config.getDir());
		config.setAccessKey(requireText(config.getAccessKey(), "AccessKey不能为空"));
		config.setAccessSecret(requireText(config.getAccessSecret(), "AccessKeySecret不能为空"));
		config.setEndpoint(requireText(config.getEndpoint(), "Endpoint不能为空"));
		validateEndpoint(config.getEndpoint());
		if (StorageTypeConstants.QINIU.equals(type) && !StringUtils.hasText(config.getDomain())) {
			throw new ArynBusinessException("七牛云存储必须配置公开访问域名");
		}
		if (!StringUtils.hasText(config.getStyleAccessEnabled())) {
			config.setStyleAccessEnabled(
					StorageTypeConstants.MINIO.equals(type) ? CommonConstants.YES : CommonConstants.NO);
		}
		if (!Set.of(CommonConstants.NO, CommonConstants.YES).contains(config.getStyleAccessEnabled())) {
			throw new ArynBusinessException("Path-style配置不正确");
		}
	}

	private void validateLocalRoot(String rootPath) {
		try {
			if (!Path.of(rootPath).isAbsolute()) {
				throw new ArynBusinessException("本地存储根目录必须是绝对路径");
			}
		}
		catch (InvalidPathException exception) {
			throw new ArynBusinessException("本地存储根目录格式不正确");
		}
	}

	private void validateBucket(String bucket) {
		if (bucket.chars().anyMatch(Character::isWhitespace) || bucket.contains("/") || bucket.contains("\\")) {
			throw new ArynBusinessException("Bucket格式不正确");
		}
	}

	private void validateObjectDirectory(String directory) {
		if (!StringUtils.hasText(directory)) {
			return;
		}
		if (directory.startsWith("/") || directory.endsWith("/") || directory.contains("//")
				|| directory.contains("\\") || directory.chars().anyMatch(Character::isWhitespace)) {
			throw new ArynBusinessException("对象目录格式不正确");
		}
		for (String segment : directory.split("/")) {
			if (".".equals(segment) || "..".equals(segment)) {
				throw new ArynBusinessException("对象目录格式不正确");
			}
		}
	}

	private void restoreMaskedSecret(String original, String submitted, java.util.function.Consumer<String> setter) {
		if (StringUtils.hasText(original) && keyDesensitization.serialize(original).equals(submitted)) {
			setter.accept(original);
		}
	}

	private void clearObjectStorageCredentials(String id) {
		super.update(Wrappers.<SysStorageConfig>lambdaUpdate()
			.eq(SysStorageConfig::getId, id)
			.set(SysStorageConfig::getAccessKey, null)
			.set(SysStorageConfig::getAccessSecret, null)
			.set(SysStorageConfig::getEndpoint, null));
	}

	private String requireText(String value, String message) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			throw new ArynBusinessException(message);
		}
		return normalized;
	}

	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private void validateEndpoint(String endpoint) {
		if (endpoint.chars().anyMatch(Character::isWhitespace)) {
			throw new ArynBusinessException("Endpoint格式不正确");
		}
		try {
			URI uri = URI.create(endpoint.startsWith("http://") || endpoint.startsWith("https://") ? endpoint
					: "https://" + endpoint);
			if (!StringUtils.hasText(uri.getHost())) {
				throw new IllegalArgumentException();
			}
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("Endpoint格式不正确");
		}
	}

	private void validateDomain(String domain) {
		if (!StringUtils.hasText(domain)) {
			return;
		}
		try {
			URI uri = URI.create(domain);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					|| !StringUtils.hasText(uri.getHost())) {
				throw new IllegalArgumentException();
			}
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("访问域名必须是有效的HTTP或HTTPS地址");
		}
	}

}
