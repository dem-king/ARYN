package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import com.aryn.cloud.user.service.IUserStatisticsService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteUserStatisticsServiceImpl implements RemoteUserStatisticsService {

	private final IUserStatisticsService userStatisticsService;

	@Override
	public List<UserTrendVO> getUserTrend(UserStatisticsDTO dto) {
		return userStatisticsService.getUserTrend(dto);
	}

}
