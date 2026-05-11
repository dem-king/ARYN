
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;

import java.io.Serializable;
import java.util.List;

/**
 * 子订单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {

	/**
	 * 通过订单ID查询子订单列表
	 *
	 * @author 雨滴kian
	 * @date 2022/6/11
	 * @param orderId
	 * @return: java.util.List<com.aryn.cloud.mall.common.entity.OrderItem>
	 */
	List<OrderItemEntity> selectByOrderId(String orderId);

	/**
	 * 查询子订单详情
	 *
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @param id
	 * @return: com.aryn.cloud.mall.common.entity.OrderItem
	 */
	OrderItemEntity selectOrderItemById(Serializable id);

}
