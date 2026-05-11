package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;

import java.util.List;

public interface IUserStatisticsService {

	/**
	 * 获取用户概览统计
	 * @return UserOverviewVO
	 */
	UserOverviewVO getUserOverview();

	/**
	 * 获取用户趋势统计 (新增用户)
	 * @param dto 统计参数
	 * @return List<UserTrendVO>
	 */
	List<UserTrendVO> getUserTrend(UserStatisticsDTO dto);

}
