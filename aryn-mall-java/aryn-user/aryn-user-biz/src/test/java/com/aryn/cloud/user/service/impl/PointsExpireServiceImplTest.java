package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分过期清零：批次剩余量清零 + 余额侧按 min(Σ到期剩余, 当前余额) 扣减 +
 * POINTS_EXPIRE 负流水（按天按用户幂等）。
 */
@ExtendWith(MockitoExtension.class)
class PointsExpireServiceImplTest {

	@Mock
	private PointsRecordMapper pointsRecordMapper;

	@Mock
	private UserInfoMapper userInfoMapper;

	private PointsExpireServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new PointsExpireServiceImpl(pointsRecordMapper, userInfoMapper);
	}

	@Test
	void expireUserClearsBatchesAndDeductsBalanceWithIdempotentRecord() {
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(new PointsRecord().setId("b-1").setRemainPoint(30),
					new PointsRecord().setId("b-2").setRemainPoint(20)));
		when(userInfoMapper.selectPointForUpdate("user-1")).thenReturn(40);
		when(pointsRecordMapper.zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class))).thenReturn(2);
		when(userInfoMapper.expirePoints("user-1", 40)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		assertTrue(service.expireUser("user-1"));

		verify(pointsRecordMapper).zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class));
		verify(userInfoMapper).expirePoints("user-1", 40);
		ArgumentCaptor<PointsRecord> captor = ArgumentCaptor.forClass(PointsRecord.class);
		verify(pointsRecordMapper).insert(captor.capture());
		assertEquals("2", captor.getValue().getChangeType());
		assertEquals(40, captor.getValue().getChangePoint());
		assertEquals(0, captor.getValue().getBalanceAfter());
		assertEquals("POINTS_EXPIRE", captor.getValue().getTriggerScene());
		assertNotNull(captor.getValue().getBizNo());
		assertTrue(captor.getValue().getBizNo().endsWith(":user-1"));
	}

	/**
	 * 余额小于到期批次合计（历史账实漂移）：按余额封顶扣减，不写成负数余额。
	 */
	@Test
	void expireUserClampsToCurrentBalance() {
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(new PointsRecord().setId("b-1").setRemainPoint(500)));
		when(userInfoMapper.selectPointForUpdate("user-1")).thenReturn(120);
		when(pointsRecordMapper.zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class))).thenReturn(1);
		when(userInfoMapper.expirePoints("user-1", 120)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		assertTrue(service.expireUser("user-1"));

		verify(userInfoMapper).expirePoints("user-1", 120);
		ArgumentCaptor<PointsRecord> captor = ArgumentCaptor.forClass(PointsRecord.class);
		verify(pointsRecordMapper).insert(captor.capture());
		assertEquals(120, captor.getValue().getChangePoint());
		assertEquals(0, captor.getValue().getBalanceAfter());
	}

	@Test
	void expireUserSkipsWhenNoExpiredBatches() {
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenReturn(List.of());

		assertFalse(service.expireUser("user-1"));

		verify(userInfoMapper, never()).expirePoints(any(), any());
		verify(pointsRecordMapper, never()).insert(any(PointsRecord.class));
	}

	/**
	 * 用户已删除（行锁查不到余额）：批次剩余量仍要清零以终止扫描，但不写流水不动余额。
	 */
	@Test
	void expireUserStillZeroesBatchesWhenUserMissing() {
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(new PointsRecord().setId("b-1").setRemainPoint(30)));
		when(userInfoMapper.selectPointForUpdate("user-1")).thenReturn(null);
		when(pointsRecordMapper.zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class))).thenReturn(1);

		assertFalse(service.expireUser("user-1"));

		verify(pointsRecordMapper).zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class));
		verify(pointsRecordMapper, never()).insert(any(PointsRecord.class));
	}

	/**
	 * 同日重复调度撞幂等键：清零已完成、流水插入撞唯一键时按已处理返回。
	 */
	@Test
	void expireUserTreatsDuplicateKeyAsIdempotent() {
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(new PointsRecord().setId("b-1").setRemainPoint(30)));
		when(userInfoMapper.selectPointForUpdate("user-1")).thenReturn(30);
		when(pointsRecordMapper.zeroExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class))).thenReturn(1);
		when(userInfoMapper.expirePoints("user-1", 30)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class)))
			.thenThrow(new org.springframework.dao.DuplicateKeyException("uk_points_record_biz"));

		assertFalse(service.expireUser("user-1"));
	}

	@Test
	void expireTenantContinuesOnSingleUserFailure() {
		when(pointsRecordMapper.selectExpiringUserIds(any(LocalDateTime.class)))
			.thenReturn(List.of("user-1", "user-2"));
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-1"), any(LocalDateTime.class)))
			.thenThrow(new IllegalStateException("库抖动"));
		when(pointsRecordMapper.selectExpiredIncomeRows(eq("user-2"), any(LocalDateTime.class)))
			.thenReturn(List.of(new PointsRecord().setId("b-1").setRemainPoint(10)));
		when(userInfoMapper.selectPointForUpdate("user-2")).thenReturn(10);
		when(pointsRecordMapper.zeroExpiredIncomeRows(eq("user-2"), any(LocalDateTime.class))).thenReturn(1);
		when(userInfoMapper.expirePoints("user-2", 10)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		assertEquals(1, service.expireTenant());
	}

}
