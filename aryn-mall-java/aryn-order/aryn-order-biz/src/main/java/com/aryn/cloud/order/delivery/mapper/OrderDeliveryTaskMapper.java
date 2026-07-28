package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;

import java.time.LocalDateTime;

/**
 * 商城配送任务 Mapper。
 */
@Mapper
public interface OrderDeliveryTaskMapper extends BaseMapper<OrderDeliveryTask> {

	OrderDeliveryTask selectByTenantAndOrderId(@Param("tenantId") String tenantId,
			@Param("orderId") String orderId);

	OrderDeliveryTask selectByTenantAndId(@Param("tenantId") String tenantId, @Param("id") String id);

	int assign(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("expectedStatus") String expectedStatus, @Param("version") int version,
			@Param("staff") DeliveryStaffVO staff, @Param("operatorId") String operatorId,
			@Param("operatorName") String operatorName, @Param("operateTime") LocalDateTime operateTime);

	int reassign(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("expectedStatus") String expectedStatus, @Param("version") int version,
			@Param("staff") DeliveryStaffVO staff, @Param("operatorId") String operatorId,
			@Param("operatorName") String operatorName, @Param("operateTime") LocalDateTime operateTime);

	int updateAdminStatus(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("expectedStatus") String expectedStatus, @Param("version") int version,
			@Param("targetStatus") String targetStatus, @Param("reasonCode") String reasonCode,
			@Param("description") String description, @Param("operateTime") LocalDateTime operateTime,
			@Param("timeField") String timeField, @Param("operatorId") String operatorId);

}
