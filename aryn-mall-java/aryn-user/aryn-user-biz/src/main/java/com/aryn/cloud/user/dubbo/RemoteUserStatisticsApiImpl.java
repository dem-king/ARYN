package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsApi;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserFunnelVO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import com.aryn.cloud.user.service.IUserStatisticsService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 远程用户统计服务实现 — 大屏专用 (Dubbo RPC)
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteUserStatisticsApiImpl implements RemoteUserStatisticsApi {

	private final IUserStatisticsService userStatisticsService;

	@Override
	public UserFunnelVO getUserFunnel(UserStatisticsDTO dto) {
		return userStatisticsService.getUserFunnel(dto);
	}

	@Override
	public Long getOnlineUserCount() {
		return userStatisticsService.getOnlineUserCount();
	}

	@Override
	public UserOverviewVO getUserOverview() {
		return userStatisticsService.getUserOverview();
	}

}