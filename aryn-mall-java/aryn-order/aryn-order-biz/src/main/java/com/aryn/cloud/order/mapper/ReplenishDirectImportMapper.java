package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.ReplenishDirectImport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 清单直下单导入任务持久层。 */
@Mapper
public interface ReplenishDirectImportMapper extends BaseMapper<ReplenishDirectImport> {

	/**
	 * 确认前锁定任务行并把归属校验放进同一条 SQL。
	 *
	 * <p>并发确认（双击、双端同时点、网络重试）必须在同一把行锁上串行化：
	 * 先读到 PENDING 再无条件 update 会让后提交的事务覆盖先提交的复核结果；
	 * 锁内重新读取状态后，第二个事务会看到 CONFIRMED 并走同一份合并清单。
	 * 非法操作人查不到行，按「不存在」处理，不暴露任务存在性。
	 */
	@Select("""
			SELECT * FROM replenish_direct_import
			WHERE tenant_id = #{tenantId}
			  AND id = #{importId}
			  AND operator_user_id = #{userId}
			  AND del_flag = '0'
			FOR UPDATE
			""")
	ReplenishDirectImport selectForConfirm(@Param("tenantId") String tenantId, @Param("importId") String importId,
			@Param("userId") String userId);

}
