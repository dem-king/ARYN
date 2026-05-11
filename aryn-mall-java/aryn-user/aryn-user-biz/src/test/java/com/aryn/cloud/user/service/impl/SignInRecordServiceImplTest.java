package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SignInConfig;
import com.aryn.cloud.user.api.entity.SignInRecord;
import com.aryn.cloud.user.api.vo.SignInResultVO;
import com.aryn.cloud.user.mapper.SignInConfigMapper;
import com.aryn.cloud.user.mapper.SignInRecordMapper;
import com.aryn.cloud.user.service.IPointsRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SignInRecordServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SignInRecordServiceImplTest {

	@Mock
	private SignInConfigMapper signInConfigMapper;

	@Mock
	private IPointsRecordService pointsRecordService;

	@Mock
	private SignInRecordMapper signInRecordMapper;

	@InjectMocks
	private SignInRecordServiceImpl signInRecordService;

	private SignInConfig day1Config;
	private SignInConfig day3Config;
	private SignInConfig day7Config;

	@BeforeEach
	void setUp() {
		// 设置 baseMapper
		signInRecordService.baseMapper = signInRecordMapper;

		day1Config = new SignInConfig();
		day1Config.setId("config-day1");
		day1Config.setConsecutiveDay(1);
		day1Config.setRewardPoint(5);
		day1Config.setStatus("0");

		day3Config = new SignInConfig();
		day3Config.setId("config-day3");
		day3Config.setConsecutiveDay(3);
		day3Config.setRewardPoint(15);
		day3Config.setStatus("0");

		day7Config = new SignInConfig();
		day7Config.setId("config-day7");
		day7Config.setConsecutiveDay(7);
		day7Config.setRewardPoint(50);
		day7Config.setStatus("0");
	}

	@Test
	@DisplayName("签到 - 首次签到(无昨日记录)连续天数为1")
	void signIn_firstSignIn_consecutiveDayIs1() {
		// given
		String userId = "user001";
		// 今日未签到
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);
		// 昨日无签到记录
		when(signInRecordMapper.selectOne(any())).thenReturn(null);
		// 签到配置：连续1天奖励5积分
		when(signInConfigMapper.selectList(any())).thenReturn(Collections.singletonList(day1Config));
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		SignInResultVO result = signInRecordService.signIn(userId);

		// then
		assertEquals(1, result.getConsecutiveDay());
		assertEquals(5, result.getRewardPoint());
		verify(signInRecordMapper).insert(argThat(record ->
				userId.equals(record.getUserId())
						&& record.getConsecutiveDay() == 1
						&& record.getRewardPoint() == 5
						&& record.getSignDate().equals(LocalDate.now())));
		verify(pointsRecordService).recordPointsChange(userId, "1", 5, "SIGN_IN", "签到奖励");
	}

	@Test
	@DisplayName("签到 - 连续签到(昨日已签到)连续天数递增")
	void signIn_consecutiveSignIn_consecutiveDayIncrements() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);

		// 昨日已签到，连续3天
		SignInRecord yesterdayRecord = new SignInRecord();
		yesterdayRecord.setUserId(userId);
		yesterdayRecord.setSignDate(LocalDate.now().minusDays(1));
		yesterdayRecord.setConsecutiveDay(3);
		when(signInRecordMapper.selectOne(any())).thenReturn(yesterdayRecord);

		// 连续4天匹配day3配置(连续3天奖励15积分，因为4天>3天，取最大匹配)
		when(signInConfigMapper.selectList(any())).thenReturn(Arrays.asList(day3Config, day1Config));
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		SignInResultVO result = signInRecordService.signIn(userId);

		// then
		assertEquals(4, result.getConsecutiveDay());
		assertEquals(15, result.getRewardPoint());
		verify(signInRecordMapper).insert(argThat(record -> record.getConsecutiveDay() == 4));
		verify(pointsRecordService).recordPointsChange(userId, "1", 15, "SIGN_IN", "签到奖励");
	}

	@Test
	@DisplayName("签到 - 今日已签到时抛出异常")
	void signIn_alreadySignedInToday() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(1L);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				signInRecordService.signIn(userId));

		assertEquals("今日已签到", exception.getMsg());
		verify(pointsRecordService, never()).recordPointsChange(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("签到 - 无签到配置时奖励积分为0且不发放积分")
	void signIn_noConfig_rewardPointIsZero() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);
		when(signInRecordMapper.selectOne(any())).thenReturn(null);
		when(signInConfigMapper.selectList(any())).thenReturn(Collections.emptyList());
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		SignInResultVO result = signInRecordService.signIn(userId);

		// then
		assertEquals(1, result.getConsecutiveDay());
		assertEquals(0, result.getRewardPoint());
		verify(pointsRecordService, never()).recordPointsChange(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("签到 - 连续7天签到匹配最高奖励配置")
	void signIn_sevenDayConsecutive_matchHighestReward() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);

		SignInRecord yesterdayRecord = new SignInRecord();
		yesterdayRecord.setConsecutiveDay(6);
		when(signInRecordMapper.selectOne(any())).thenReturn(yesterdayRecord);

		// 连续7天，匹配day7配置(50积分)
		when(signInConfigMapper.selectList(any())).thenReturn(Arrays.asList(day7Config, day3Config, day1Config));
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		SignInResultVO result = signInRecordService.signIn(userId);

		// then
		assertEquals(7, result.getConsecutiveDay());
		assertEquals(50, result.getRewardPoint());
		verify(pointsRecordService).recordPointsChange(userId, "1", 50, "SIGN_IN", "签到奖励");
	}

	@Test
	@DisplayName("签到 - 签到记录正确保存日期和连续天数")
	void signIn_recordSavedCorrectly() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);
		when(signInRecordMapper.selectOne(any())).thenReturn(null);
		when(signInConfigMapper.selectList(any())).thenReturn(Collections.singletonList(day1Config));
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		signInRecordService.signIn(userId);

		// then
		verify(signInRecordMapper).insert(argThat(record ->
				userId.equals(record.getUserId())
						&& record.getSignDate().equals(LocalDate.now())
						&& record.getConsecutiveDay() == 1
						&& record.getRewardPoint() == 5));
	}

	@Test
	@DisplayName("签到 - 中断后重新签到连续天数从1开始")
	void signIn_afterBreak_consecutiveDayResetsTo1() {
		// given
		String userId = "user001";
		when(signInRecordMapper.selectCount(any())).thenReturn(0L);
		// 昨日无签到记录（中断了）
		when(signInRecordMapper.selectOne(any())).thenReturn(null);
		when(signInConfigMapper.selectList(any())).thenReturn(Collections.singletonList(day1Config));
		when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);

		// when
		SignInResultVO result = signInRecordService.signIn(userId);

		// then
		assertEquals(1, result.getConsecutiveDay());
	}

}
