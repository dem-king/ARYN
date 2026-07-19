
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.OrderItemEntity;

/**
 * 子订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
public interface IOrderItemService extends IService<OrderItemEntity> {

	/**
	 * 查询详情
	 *
	 * @author 雨滴kian
	 * @date 2022/7/1
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.OrderItem
	 */
	OrderItemEntity getOrderItemById(String id);

	OrderItemEntity getUserOrderItemById(String id, String userId);

}
