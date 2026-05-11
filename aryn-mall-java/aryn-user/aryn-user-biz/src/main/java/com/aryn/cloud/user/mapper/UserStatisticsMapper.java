package com.aryn.cloud.user.mapper;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户统计 Mapper
 */
@Mapper
public interface UserStatisticsMapper {

	/**
	 * 获取用户概览统计
	 * @return UserOverviewVO
	 */
	UserOverviewVO getUserOverview();

	/**
	 * 获取用户趋势统计 (新增用户)
	 * @param dto 统计参数
	 * @param format 时间格式
	 * @return List<UserTrendVO>
	 */
	List<UserTrendVO> getUserTrend(@Param("dto") UserStatisticsDTO dto, @Param("format") String format);

}
