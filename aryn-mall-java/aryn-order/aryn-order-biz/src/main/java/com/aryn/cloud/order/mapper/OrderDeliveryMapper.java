
package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.OrderDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 发货单
 *
 * @author 雨滴kian
 * @since 2025/4/27
 */
@Mapper
public interface OrderDeliveryMapper extends BaseMapper<OrderDelivery> {

	/**
	 * 根据订单号查询发货单
	 * @param orderId
	 * @return
	 */
	OrderDelivery selectByOrderId(@Param("orderId") String orderId);

}
