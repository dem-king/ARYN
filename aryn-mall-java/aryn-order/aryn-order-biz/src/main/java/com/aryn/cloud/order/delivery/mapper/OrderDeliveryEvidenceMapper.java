package com.aryn.cloud.order.delivery.mapper;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 商城配送履约凭证 Mapper。 */
@Mapper
public interface OrderDeliveryEvidenceMapper extends BaseMapper<OrderDeliveryEvidence> {

	@Update("""
		UPDATE order_delivery_evidence
		SET binding_status = 'BOUND', update_time = NOW()
		WHERE tenant_id = #{tenantId} AND task_id = #{taskId}
			AND binding_status = 'PENDING' AND del_flag = '0'
		""")
	int markBoundByTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
		SELECT * FROM order_delivery_evidence
		WHERE binding_status = 'PENDING' AND del_flag = '0'
		ORDER BY create_time ASC
		LIMIT #{limit}
		""")
	List<OrderDeliveryEvidence> selectPendingBindings(@Param("limit") int limit);
}
