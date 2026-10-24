package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.PointsRecord;
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
