package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.vo.PointsLiabilityDailyVO;
import com.aryn.cloud.user.api.vo.PointsLiabilityReportVO;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

	IPage<PointsRecordVO> selectRecordPage(Page page, @Param("nickname") String nickname,
			@Param("changeType") String changeType, @Param("beginTime") String beginTime,
			@Param("endTime") String endTime);

	IPage<PointsRecordVO> selectUserRecordPage(Page page, @Param("userId") String userId);

	/**
	 * 积分负债日报：按日拆解发放/返还/抵现/兑换/过期/后台扣减（财务递延负债口径原始聚合）。
	 * 日期参数 yyyy-MM-dd，null 表示不限制该侧。
	 */
	@Select("""
			<script>
			SELECT DATE(create_time) AS stat_date,
				COALESCE(SUM(CASE WHEN change_type = '1' AND trigger_scene != 'ORDER_REFUND' THEN change_point ELSE 0 END), 0) AS grant_points,
				COALESCE(SUM(CASE WHEN change_type = '1' AND trigger_scene = 'ORDER_REFUND' THEN change_point ELSE 0 END), 0) AS refund_return_points,
				COALESCE(SUM(CASE WHEN trigger_scene = 'ORDER_DEDUCT' THEN change_point ELSE 0 END), 0) AS deduct_points,
				COALESCE(SUM(CASE WHEN trigger_scene = 'POINTS_EXCHANGE' THEN change_point ELSE 0 END), 0) AS exchange_points,
				COALESCE(SUM(CASE WHEN trigger_scene = 'POINTS_EXPIRE' THEN change_point ELSE 0 END), 0) AS expire_points,
				COALESCE(SUM(CASE WHEN change_type = '2' AND trigger_scene = 'ADMIN_ADJUST' THEN change_point ELSE 0 END), 0) AS admin_deduct_points
			FROM points_record
			<where>
				<if test="beginDate != null and beginDate != ''">AND create_time &gt;= CONCAT(#{beginDate}, ' 00:00:00')</if>
				<if test="endDate != null and endDate != ''">AND create_time &lt;= CONCAT(#{endDate}, ' 23:59:59')</if>
			</where>
			GROUP BY DATE(create_time)
			ORDER BY stat_date DESC
			</script>
			""")
	List<PointsLiabilityDailyVO> selectLiabilityDaily(@Param("beginDate") String beginDate,
			@Param("endDate") String endDate);

	/**
	 * 积分负债全量累计（单行）：累计发放/抵现/兑换/过期/返还。
	 */
	@Select("""
			SELECT
				COALESCE(SUM(CASE WHEN change_type = '1' AND trigger_scene != 'ORDER_REFUND' THEN change_point ELSE 0 END), 0) AS totalGrantPoints,
				COALESCE(SUM(CASE WHEN trigger_scene = 'ORDER_DEDUCT' THEN change_point ELSE 0 END), 0) AS totalDeductPoints,
				COALESCE(SUM(CASE WHEN trigger_scene = 'POINTS_EXCHANGE' THEN change_point ELSE 0 END), 0) AS totalExchangePoints,
				COALESCE(SUM(CASE WHEN trigger_scene = 'POINTS_EXPIRE' THEN change_point ELSE 0 END), 0) AS totalExpirePoints,
				COALESCE(SUM(CASE WHEN change_type = '1' AND trigger_scene = 'ORDER_REFUND' THEN change_point ELSE 0 END), 0) AS totalRefundReturnPoints
			FROM points_record
			""")
	PointsLiabilityReportVO.Summary selectLiabilitySummary();

	@Select("SELECT user_id FROM points_record WHERE remain_point > 0 AND expire_time <= #{now} GROUP BY user_id")
	List<String> selectExpiringUserIds(@Param("now") LocalDateTime now);

	@Select("SELECT id, user_id, remain_point FROM points_record "
			+ "WHERE user_id = #{userId} AND remain_point > 0 AND expire_time <= #{now}")
	List<PointsRecord> selectExpiredIncomeRows(@Param("userId") String userId, @Param("now") LocalDateTime now);

	@Update("UPDATE points_record SET remain_point = 0 "
			+ "WHERE user_id = #{userId} AND remain_point > 0 AND expire_time <= #{now}")
	int zeroExpiredIncomeRows(@Param("userId") String userId, @Param("now") LocalDateTime now);

	@Select("SELECT COALESCE(SUM(remain_point), 0) FROM points_record "
			+ "WHERE user_id = #{userId} AND remain_point > 0 AND expire_time > #{now} AND expire_time <= #{until}")
	Integer sumRemainPointsBetween(@Param("userId") String userId, @Param("now") LocalDateTime now,
			@Param("until") LocalDateTime until);

	@Select("SELECT MIN(expire_time) FROM points_record "
			+ "WHERE user_id = #{userId} AND remain_point > 0 AND expire_time > #{now} AND expire_time <= #{until}")
	LocalDateTime selectNearestExpireTime(@Param("userId") String userId, @Param("now") LocalDateTime now,
			@Param("until") LocalDateTime until);

}
