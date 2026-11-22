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

	/**
	 * 原子占用限量（件数，档期特供 type=12）：额度内才累加，返回 0 表示限量已被并发订单占完。
	 * total_quota 为 NULL/0 视为不限，仍累计消耗值供运营查看。
	 */
	@Update("""
			UPDATE `promotion_activity`
			   SET `consumed_quota` = `consumed_quota` + #{units},
			       `update_time` = NOW()
			 WHERE `id` = #{activityId}
			   AND `tenant_id` = #{tenantId}
			   AND `del_flag` = '0'
			   AND (`total_quota` IS NULL OR `total_quota` <= 0 OR `consumed_quota` + #{units} <= `total_quota`)
			""")
	int consumeQuota(@Param("tenantId") String tenantId, @Param("activityId") String activityId,
			@Param("units") int units);

	/** 原子归还限量（取消/超时/退款），下限为 0。 */
	@Update("""
			UPDATE `promotion_activity`
			   SET `consumed_quota` = GREATEST(`consumed_quota` - #{units}, 0),
			       `update_time` = NOW()
			 WHERE `id` = #{activityId}
			   AND `tenant_id` = #{tenantId}
			   AND `del_flag` = '0'
			""")
	int releaseQuota(@Param("tenantId") String tenantId, @Param("activityId") String activityId,
			@Param("units") int units);

	/**
	 * 退款按锁回补限量（档期特供）：锁上已回补件数原子累计做幂等闸门，
	 * 累计不超过本锁占用件数（quotaUnits）才回补活动 consumed_quota。
	 * 返回 0 表示重复投递/超额回补被拒；调用方须与活动回补同事务。
	 */
	@Update("""
			UPDATE `promotion_lock`
			   SET `refunded_quota_units` = `refunded_quota_units` + #{units},
			       `update_time` = NOW()
			 WHERE `id` = #{lockId}
			   AND `tenant_id` = #{tenantId}
			   AND `del_flag` = '0'
			   AND `refunded_quota_units` + #{units} <= #{quotaUnits}
			""")
	int accumulateRefundedQuota(@Param("tenantId") String tenantId, @Param("lockId") String lockId,
			@Param("units") int units, @Param("quotaUnits") int quotaUnits);

}
