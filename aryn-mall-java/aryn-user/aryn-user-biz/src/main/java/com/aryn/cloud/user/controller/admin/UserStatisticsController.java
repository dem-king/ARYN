package com.aryn.cloud.user.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import com.aryn.cloud.user.service.IUserStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/statistics")
@Tag(description = "userstatistics", name = "用户统计")
public class UserStatisticsController {

	private final IUserStatisticsService userStatisticsService;

	@GetMapping("/overview")
	@Operation(summary = "用户概览统计")
	public Result<UserOverviewVO> getUserOverview() {
		return Result.success(userStatisticsService.getUserOverview());
	}

	@GetMapping("/trend")
	@Operation(summary = "用户趋势统计 (新增用户)")
	public Result<List<UserTrendVO>> getUserTrend(UserStatisticsDTO dto) {
		return Result.success(userStatisticsService.getUserTrend(dto));
	}

}
