package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商城配送任务审计日志 Mapper。 */
@Mapper
public interface OrderDeliveryTaskLogMapper extends BaseMapper<OrderDeliveryTaskLog> {

	@Select("""
		SELECT * FROM order_delivery_task_log
		WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND action = #{action}
			AND request_id = #{requestId} AND del_flag = '0'
		LIMIT 1
		""")
	OrderDeliveryTaskLog selectByRequest(@Param("tenantId") String tenantId, @Param("taskId") String taskId,
			@Param("action") String action, @Param("requestId") String requestId);
}
