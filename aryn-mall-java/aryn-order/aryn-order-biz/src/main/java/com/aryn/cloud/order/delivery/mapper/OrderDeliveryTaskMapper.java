package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城配送任务 Mapper。
 */
@Mapper
public interface OrderDeliveryTaskMapper extends BaseMapper<OrderDeliveryTask> {

	OrderDeliveryTask selectByTenantAndOrderId(@Param("tenantId") String tenantId,
			@Param("orderId") String orderId);

}
