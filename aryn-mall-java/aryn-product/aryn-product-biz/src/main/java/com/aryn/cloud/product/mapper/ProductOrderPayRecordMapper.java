package com.aryn.cloud.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductOrderPayRecordMapper {

	@Insert("""
			INSERT IGNORE INTO product_order_pay_record
				(id, order_id, tenant_id, create_time, del_flag)
			VALUES
				(#{id}, #{orderId}, #{tenantId}, NOW(), '0')
			""")
	int insertIfAbsent(@Param("id") String id, @Param("orderId") String orderId,
			@Param("tenantId") String tenantId);

}
