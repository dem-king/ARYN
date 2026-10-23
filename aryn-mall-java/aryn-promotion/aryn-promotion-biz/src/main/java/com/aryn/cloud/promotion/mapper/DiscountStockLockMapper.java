package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.DiscountStockLock;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 折扣商品库存占用明细。
 */
@Mapper
public interface DiscountStockLockMapper extends BaseMapper<DiscountStockLock> {

	/**
	 * 释放某一订单的全部占用行：只改 status，不改 quantity（保留审计），
	 * 已释放的行不重复处理（status='1' 条件保证幂等）。
	 */
	@Update("""
		UPDATE discount_stock_lock
		SET status = '0', release_reason = #{reason}, update_time = NOW()
		WHERE tenant_id = #{tenantId} AND order_id = #{orderId} AND status = '1' AND del_flag = '0'
		""")
	int releaseByOrder(@Param("tenantId") String tenantId, @Param("orderId") String orderId,
			@Param("reason") String reason);

	/**
	 * 只释放某一订单明细的占用（部分退款）：同订单其它明细保持占用。
	 */
	@Update("""
		UPDATE discount_stock_lock
		SET status = '0', release_reason = #{reason}, update_time = NOW()
		WHERE tenant_id = #{tenantId} AND order_id = #{orderId} AND order_item_id = #{orderItemId}
		  AND status = '1' AND del_flag = '0'
		""")
	int releaseByOrderItem(@Param("tenantId") String tenantId, @Param("orderId") String orderId,
			@Param("orderItemId") String orderItemId, @Param("reason") String reason);

}