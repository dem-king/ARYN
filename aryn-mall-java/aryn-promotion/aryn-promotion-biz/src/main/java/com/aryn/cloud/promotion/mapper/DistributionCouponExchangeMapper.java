package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.vo.DistributionCouponCostVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 分销佣金兑换优惠券记录
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Mapper
public interface DistributionCouponExchangeMapper extends BaseMapper<DistributionCouponExchange> {

	/**
	 * 券成本汇总（营销费用口径）：仅统计已发放成功的兑换记录。
	 * @param query 过滤条件（userId / packId）
	 * @param createTimeStart 创建时间起（可空）
	 * @param createTimeEnd 创建时间止（可空）
	 * @return 兑换笔数、消耗佣金合计、券面额合计
	 */
	@Select("""
			<script>
			SELECT COUNT(*) AS exchangeCount,
			       COALESCE(SUM(commission_amount), 0) AS commissionAmount,
			       COALESCE(SUM(face_amount), 0) AS faceAmount
			FROM distribution_coupon_exchange
			WHERE del_flag = '0'
			  AND status = '1'
			<if test="query != null and query.userId != null and query.userId != ''">
			  AND user_id = #{query.userId}
			</if>
			<if test="query != null and query.packId != null and query.packId != ''">
			  AND pack_id = #{query.packId}
			</if>
			<if test="createTimeStart != null">
			  AND create_time &gt;= #{createTimeStart}
			</if>
			<if test="createTimeEnd != null">
			  AND create_time &lt;= #{createTimeEnd}
			</if>
			</script>
			""")
	DistributionCouponCostVO selectCost(@Param("query") DistributionCouponExchange query,
			@Param("createTimeStart") LocalDateTime createTimeStart,
			@Param("createTimeEnd") LocalDateTime createTimeEnd);

}
