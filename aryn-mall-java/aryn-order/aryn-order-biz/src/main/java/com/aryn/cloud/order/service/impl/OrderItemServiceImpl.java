
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.service.IOrderItemService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 子订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
@Service
@AllArgsConstructor
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItemEntity> implements IOrderItemService {

	@Override
	public OrderItemEntity getOrderItemById(String id) {
		return baseMapper.selectOrderItemById(id);
	}

}
