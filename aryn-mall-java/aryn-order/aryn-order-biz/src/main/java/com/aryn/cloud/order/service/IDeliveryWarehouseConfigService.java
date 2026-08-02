
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;

/**
 * 仓库配置
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryWarehouseConfigService extends IService<DeliveryWarehouseConfig> {

	/**
	 * 获取当前租户的仓库配置
	 * @return 仓库配置
	 */
	DeliveryWarehouseConfig getConfig();

	/**
	 * 保存或更新仓库配置（租户级单条）
	 * @param config 配置
	 * @return 是否成功
	 */
	boolean saveOrUpdateConfig(DeliveryWarehouseConfig config);

}