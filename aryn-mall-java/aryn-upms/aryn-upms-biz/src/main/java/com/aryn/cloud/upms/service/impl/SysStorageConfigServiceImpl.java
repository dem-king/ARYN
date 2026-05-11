
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.desensitization.KeyDesensitization;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.upms.api.entity.SysStorageConfig;
import com.aryn.cloud.upms.mapper.SysStorageConfigMapper;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

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

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	@Override
	public SysStorageConfigDTO getConfig() {
		return baseMapper.selectConfig();
	}

	@Override
	public boolean saveStorageConfig(SysStorageConfig sysStorageConfig) {
		if (sysStorageConfig.getStatus().equals(CommonConstants.NORMAL_STATUS)) {
			// 更改其他启用配置
			updateStatus();
		}
		return this.save(sysStorageConfig);
	}

	@Override
	public boolean updateStorageConfigById(SysStorageConfig sysStorageConfig) {
		SysStorageConfig target = this.getById(sysStorageConfig.getId());
		if (Objects.isNull(target)) {
			throw new ArynBusinessException("文件配置不存在");
		}

		if (StringUtils.hasText(target.getAccessKey())
				&& keyDesensitization.serialize(target.getAccessKey()).equals(sysStorageConfig.getAccessKey())) {
			sysStorageConfig.setAccessKey(null);
		}
		if (StringUtils.hasText(target.getAccessSecret())
				&& keyDesensitization.serialize(target.getAccessSecret()).equals(sysStorageConfig.getAccessSecret())) {
			sysStorageConfig.setAccessSecret(null);
		}
		if (sysStorageConfig.getStatus().equals(CommonConstants.NORMAL_STATUS)) {
			// 更改其他启用配置
			updateStatus();
		}

		return this.updateById(sysStorageConfig);
	}

	public void updateStatus() {
		SysStorageConfig sysStorageConfig = new SysStorageConfig();
		sysStorageConfig.setStatus(CommonConstants.YES);
		super.update(sysStorageConfig, Wrappers.<SysStorageConfig>lambdaQuery()
			.eq(SysStorageConfig::getStatus, CommonConstants.NORMAL_STATUS));
	}

}
