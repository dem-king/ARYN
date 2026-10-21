package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserSourceVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;

import java.util.List;

/**
 * 远程用户统计服务
 */
public interface RemoteUserStatisticsService {

	/**
	 * 获取用户趋势统计 (新增用户)
	 * @param dto 统计参数
	 * @return List<UserTrendVO>
	 */
	List<UserTrendVO> getUserTrend(UserStatisticsDTO dto);

	/**
	 * 批量获取用户来源
	 * @param userIds 用户ID集合
	 * @return List<UserSourceVO>
	 */
	List<UserSourceVO> getUserSources(List<String> userIds);

}
