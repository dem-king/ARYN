
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.OrderConfig;

/**
 * 订单配置
 *
 * @author 雨滴kian
 * @date 2025/5/27
 */
public interface IOrderConfigService extends IService<OrderConfig> {

	boolean saveConfig(OrderConfig orderConfig);

	boolean updateConfigById(OrderConfig orderConfig);

	OrderConfig getConfig();

	boolean removeConfigById(String id);

}
