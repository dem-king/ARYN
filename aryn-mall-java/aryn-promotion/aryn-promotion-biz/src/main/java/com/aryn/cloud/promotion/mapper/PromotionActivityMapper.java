package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** PromotionActivity 持久层。 */
@Mapper
public interface PromotionActivityMapper extends BaseMapper<PromotionActivity> {

	/**
	 * 原子占用预算：额度内才累加，返回 0 表示预算已被并发订单用尽。
	 * total_budget=0 视为不限，仍累计消耗值供运营查看。
	 */
	@Update("""
			UPDATE `promotion_activity`
			   SET `consumed_budget` = `consumed_budget` + #{amount},
			       `update_time` = NOW()
			 WHERE `id` = #{activityId}
			   AND `tenant_id` = #{tenantId}
			   AND `del_flag` = '0'
			   AND (`total_budget` = 0 OR `consumed_budget` + #{amount} <= `total_budget`)
			""")
	int consumeBudget(@Param("tenantId") String tenantId, @Param("activityId") String activityId,
			@Param("amount") BigDecimal amount);

	/** 原子归还预算（取消/超时/退款），下限为 0，避免历史脏数据把余额扣成负数。 */
	@Update("""
			UPDATE `promotion_activity`
			   SET `consumed_budget` = GREATEST(`consumed_budget` - #{amount}, 0),
			       `update_time` = NOW()
			 WHERE `id` = #{activityId}
			   AND `tenant_id` = #{tenantId}
			   AND `del_flag` = '0'
			""")
	int releaseBudget(@Param("tenantId") String tenantId, @Param("activityId") String activityId,
			@Param("amount") BigDecimal amount);

}
