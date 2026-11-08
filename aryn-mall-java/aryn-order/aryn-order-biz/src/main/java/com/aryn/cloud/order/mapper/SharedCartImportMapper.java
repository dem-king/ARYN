package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.SharedCartImport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 补给单 Excel 导入任务持久层。 */
@Mapper
public interface SharedCartImportMapper extends BaseMapper<SharedCartImport> {

	/**
	 * 确认并入前锁定导入任务行。
	 *
	 * <p>与清单直下单确认同一类并发风险：先读到待确认、再无条件 update，
	 * 双击/双端/重试会让两个事务都执行并入，造成明细数量翻倍。
	 * 锁内重新读取后，第二个事务会看到已并入并直接走幂等返回。
	 */
	@Select("""
			SELECT * FROM shared_cart_import
			WHERE tenant_id = #{tenantId}
			  AND cart_id = #{cartId}
			  AND id = #{importId}
			  AND del_flag = '0'
			FOR UPDATE
			""")
	SharedCartImport selectForConfirm(@Param("tenantId") String tenantId, @Param("cartId") String cartId,
			@Param("importId") String importId);

}
