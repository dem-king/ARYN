package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.vo.UserOverviewVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import com.aryn.cloud.user.mapper.UserStatisticsMapper;
import com.aryn.cloud.user.service.IUserStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserStatisticsServiceImpl implements IUserStatisticsService {

	private final UserStatisticsMapper userStatisticsMapper;

	@Override
	public UserOverviewVO getUserOverview() {
		return userStatisticsMapper.getUserOverview();
	}

	@Override
	public List<UserTrendVO> getUserTrend(UserStatisticsDTO dto) {
		LocalDateTime startTime = dto.getStartTime();
		LocalDateTime endTime = dto.getEndTime();
		String format;
		List<String> timePoints = new ArrayList<>();
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
			// 同一天，按小时分组
			format = "%H";
			// 生成小时列表 00-23 (或者 startHour - endHour)
			int startHour = startTime.getHour();
			int endHour = endTime.getHour();
			for (int i = startHour; i <= endHour; i++) {
				timePoints.add(String.format("%02d", i));
			}
		}
		else {
			// 不同天，按日期分组
			format = "%Y-%m-%d";
			LocalDateTime temp = startTime;
			// 按天遍历
			while (!temp.toLocalDate().isAfter(endTime.toLocalDate())) {
				timePoints.add(temp.format(dateFormatter));
				temp = temp.plusDays(1);
			}
		}

		List<UserTrendVO> dbList = userStatisticsMapper.getUserTrend(dto, format);
		Map<String, UserTrendVO> dbMap = dbList.stream()
			.collect(Collectors.toMap(UserTrendVO::getTimePoint, v -> v, (v1, v2) -> v1));

		List<UserTrendVO> result = new ArrayList<>();
		for (String tp : timePoints) {
			UserTrendVO vo = dbMap.get(tp);
			if (vo == null) {
				vo = new UserTrendVO();
				vo.setTimePoint(tp);
				vo.setNewUserCount(0);
			}
			result.add(vo);
		}
		return result;
	}

}
