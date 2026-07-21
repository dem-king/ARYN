package com.aryn.cloud.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductRefundStockRecordMapper {

	@Insert("""
			INSERT IGNORE INTO product_refund_stock_record
				(id, refund_no, tenant_id, create_time, del_flag)
			VALUES
				(#{id}, #{refundNo}, #{tenantId}, NOW(), '0')
			""")
	int insertIfAbsent(@Param("id") String id, @Param("refundNo") String refundNo, @Param("tenantId") String tenantId);

}
