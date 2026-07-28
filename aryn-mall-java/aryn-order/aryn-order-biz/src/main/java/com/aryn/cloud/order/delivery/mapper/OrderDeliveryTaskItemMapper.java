package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商城配送配货明细 Mapper。
 */
@Mapper
public interface OrderDeliveryTaskItemMapper extends BaseMapper<OrderDeliveryTaskItem> {

	@Update("""
		UPDATE order_delivery_task_item
		SET attempt_no = #{attemptNo}, checked = '0', checked_by = NULL, checked_at = NULL,
			update_time = NOW()
		WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND del_flag = '0'
		""")
	int resetForAttempt(@Param("tenantId") String tenantId, @Param("taskId") String taskId,
			@Param("attemptNo") int attemptNo);

	@org.apache.ibatis.annotations.Select("""
		SELECT COUNT(*) FROM order_delivery_task_item
		WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND attempt_no = #{attemptNo}
			AND checked = '0' AND del_flag = '0'
		""")
	int countUnchecked(@Param("tenantId") String tenantId, @Param("taskId") String taskId,
			@Param("attemptNo") int attemptNo);

	@Update("""
		UPDATE order_delivery_task_item
		SET checked = #{checked}, checked_by = #{staffId},
			checked_at = CASE WHEN #{checked} = '1' THEN NOW() ELSE NULL END, update_time = NOW()
		WHERE tenant_id = #{tenantId} AND task_id = #{taskId} AND id = #{itemId}
			AND attempt_no = #{attemptNo} AND del_flag = '0'
		""")
	int updateChecked(@Param("tenantId") String tenantId, @Param("taskId") String taskId,
			@Param("itemId") String itemId, @Param("attemptNo") int attemptNo,
			@Param("checked") String checked, @Param("staffId") String staffId);
}
