
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;
import com.aryn.cloud.order.mapper.DeliveryWarehouseConfigMapper;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仓库配置
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryWarehouseConfigServiceImpl extends ServiceImpl<DeliveryWarehouseConfigMapper, DeliveryWarehouseConfig>
		implements IDeliveryWarehouseConfigService {

	@Override
	public DeliveryWarehouseConfig getConfig() {
		return getOne(Wrappers.<DeliveryWarehouseConfig>lambdaQuery().last("LIMIT 1"));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveOrUpdateConfig(DeliveryWarehouseConfig config) {
		DeliveryWarehouseConfig existing = getConfig();
		if (existing == null) {
			if (StrUtil.isBlank(config.getTenantId())) {
				config.setTenantId(SecurityUtils.getTenantId());
			}
			return save(config);
		}
		config.setId(existing.getId());
		return updateById(config);
	}

}