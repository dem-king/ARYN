package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.SharedCart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** SharedCart 持久层。 */
@Mapper
public interface SharedCartMapper extends BaseMapper<SharedCart> {

	/**
	 * 并入明细前锁定购物车行。
	 *
	 * <p>导入任务行锁只能串行化「同一个导入」的重复确认；同一购物车的
	 * **不同导入任务**并发确认时，各自的任务锁互不阻塞，仍会同时对
	 * shared_cart_item 做读-改-写，造成数量丢失更新或撞唯一键。
	 * 这里把同车的所有并入串行化到购物车行上。
	 */
	@Select("""
			SELECT * FROM shared_cart
			WHERE tenant_id = #{tenantId}
			  AND id = #{cartId}
			  AND del_flag = '0'
			FOR UPDATE
			""")
	SharedCart selectForUpdate(@Param("tenantId") String tenantId, @Param("cartId") String cartId);

}
