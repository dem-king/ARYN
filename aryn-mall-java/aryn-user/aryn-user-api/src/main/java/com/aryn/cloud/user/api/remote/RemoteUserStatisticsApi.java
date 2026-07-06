package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserFunnelVO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;

/**
 * 远程用户统计服务 (Dubbo RPC) — 大屏专用
 */
public interface RemoteUserStatisticsApi {

	/**
	 * 获取用户漏斗统计 (注册→下单→复购)
	 * @param dto 统计参数
	 * @return UserFunnelVO
	 */
	UserFunnelVO getUserFunnel(UserStatisticsDTO dto);

	/**
	 * 获取在线用户数 (近30分钟活跃用户)
	 * @return Long
	 */
	Long getOnlineUserCount();

	/**
	 * 获取用户概览统计
	 * @return UserOverviewVO
	 */
	UserOverviewVO getUserOverview();

}