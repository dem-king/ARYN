package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsRecordServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private IMemberLevelService memberLevelService;

	@Mock
	private PointsRecordMapper pointsRecordMapper;

	private PointsRecordServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestPointsRecordService(userInfoMapper, memberLevelService, pointsRecordMapper);
	}

	@Test
	void acquirePointsUsesAtomicUpdateAndIncreasesTotalPoint() {
		UserInfo before = user(100, 500);
		UserInfo after = user(150, 550);
		when(userInfoMapper.selectById("user001")).thenReturn(before, after);
		when(userInfoMapper.acquirePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		service.recordPointsChange("user001", "1", 50, "ORDER_REWARD", "下单奖励");

		verify(userInfoMapper).acquirePoints("user001", 50);
		verify(pointsRecordMapper).insert(org.mockito.ArgumentMatchers.<PointsRecord>argThat(record ->
				record.getBalanceAfter() == 150 && record.getChangePoint() == 50));
		verify(memberLevelService).recalculateLevel("user001");
	}

	@Test
	void consumePointsUsesGuardedAtomicUpdateWithoutReducingTotalPoint() {
		UserInfo before = user(100, 500);
		UserInfo after = user(70, 500);
		when(userInfoMapper.selectById("user001")).thenReturn(before, after);
		when(userInfoMapper.consumePoints("user001", 30)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		service.recordPointsChange("user001", "2", 30, "EXCHANGE", "积分兑换");

		verify(userInfoMapper).consumePoints("user001", 30);
		verify(memberLevelService, never()).recalculateLevel("user001");
	}

	@Test
	void consumePointsRejectsConcurrentInsufficientBalance() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500));
		when(userInfoMapper.consumePoints("user001", 100)).thenReturn(0);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.recordPointsChange("user001", "2", 100, "EXCHANGE", "积分兑换"));

		assertEquals("积分余额不足", exception.getMsg());
		verify(pointsRecordMapper, never()).insert(any(PointsRecord.class));
	}

	@Test
	void rejectsUnknownChangeTypeAndNonPositiveAmount() {
		assertEquals("积分变动类型不合法", assertThrows(ArynBusinessException.class,
				() -> service.recordPointsChange("user001", "3", 10, "MANUAL", "调整")).getMsg());
		assertEquals("积分变动值必须大于0", assertThrows(ArynBusinessException.class,
				() -> service.recordPointsChange("user001", "1", 0, "MANUAL", "调整")).getMsg());
		verify(userInfoMapper, never()).selectById(any());
	}

	@Test
	void missingUserIsRejected() {
		when(userInfoMapper.selectById("user001")).thenReturn(null);

		assertEquals("用户不存在", assertThrows(ArynBusinessException.class,
				() -> service.recordPointsChange("user001", "1", 10, "MANUAL", "调整")).getMsg());
	}

	@Test
	void levelFailurePropagatesSoTransactionCanRollback() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500), user(150, 550));
		when(userInfoMapper.acquirePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		doThrow(new IllegalStateException("等级服务异常")).when(memberLevelService).recalculateLevel("user001");

		assertThrows(IllegalStateException.class,
				() -> service.recordPointsChange("user001", "1", 50, "ORDER_REWARD", "下单奖励"));
	}

	private UserInfo user(int point, int totalPoint) {
		return new UserInfo().setId("user001").setPoint(point).setTotalPoint(totalPoint);
	}

	private static final class TestPointsRecordService extends PointsRecordServiceImpl {

		private TestPointsRecordService(UserInfoMapper userInfoMapper, IMemberLevelService memberLevelService,
				PointsRecordMapper pointsRecordMapper) {
			super(userInfoMapper, memberLevelService);
			this.baseMapper = pointsRecordMapper;
		}
	}

}
