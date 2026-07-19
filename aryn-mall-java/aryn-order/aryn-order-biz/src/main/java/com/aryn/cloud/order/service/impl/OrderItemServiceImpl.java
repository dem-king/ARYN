
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
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

	private final OrderInfoMapper orderInfoMapper;

	@Override
	public OrderItemEntity getOrderItemById(String id) {
		return baseMapper.selectOrderItemById(id);
	}

	@Override
	public OrderItemEntity getUserOrderItemById(String id, String userId) {
		OrderItemEntity item = baseMapper.selectOrderItemById(id);
		if (item == null) {
			return null;
		}
		Long count = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
			.eq(OrderInfo::getId, item.getOrderId())
			.eq(OrderInfo::getUserId, userId));
		return count > 0 ? item : null;
	}

}
