package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import org.mockito.ArgumentCaptor;
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

	@Mock
	private IPointsGlobalConfigService pointsGlobalConfigService;

	private PointsRecordServiceImpl service;

	@BeforeEach
	void setUp() {
		// FIFO 批次扣减构建 lambda wrapper，纯 Mockito 环境需先注册实体元数据
		TableInfoHelper.initTableInfo(
				new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), ""),
				PointsRecord.class);
		service = new TestPointsRecordService(userInfoMapper, memberLevelService, pointsRecordMapper,
				pointsGlobalConfigService);
	}

	@Test
	void acquirePointsUsesAtomicUpdateAndIncreasesTotalPoint() {
		UserInfo before = user(100, 500);
		UserInfo after = user(150, 550);
		when(userInfoMapper.selectById("user001")).thenReturn(before, after);
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
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
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
		when(userInfoMapper.consumePoints("user001", 30)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		service.recordPointsChange("user001", "2", 30, "EXCHANGE", "积分兑换");

		verify(userInfoMapper).consumePoints("user001", 30);
		verify(memberLevelService, never()).recalculateLevel("user001");
	}

	@Test
	void consumePointsRejectsConcurrentInsufficientBalance() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500));
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
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
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
		when(userInfoMapper.acquirePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		doThrow(new IllegalStateException("等级服务异常")).when(memberLevelService).recalculateLevel("user001");

		assertThrows(IllegalStateException.class,
				() -> service.recordPointsChange("user001", "1", 50, "ORDER_REWARD", "下单奖励"));
	}

	/**
	 * 退款/取消返还：只恢复可用积分，不增加 total_point（累计获得），也不触发等级重算，
	 * 避免「下单-取消」循环刷高累计积分与积分线等级。
	 */
	@Test
	void refundSceneRestoresPointWithoutIncreasingTotalPoint() {
		UserInfo before = user(10, 500);
		UserInfo after = user(110, 500);
		when(userInfoMapper.selectById("user001")).thenReturn(before, after);
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(10);
		when(userInfoMapper.refundPoints("user001", 100)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		service.recordPointsChange("user001", "1", 100, "ORDER_REFUND", "订单取消返还积分",
				"ORDER_REFUND:NO-1");

		verify(userInfoMapper).refundPoints("user001", 100);
		verify(userInfoMapper, never()).acquirePoints(any(), any());
		verify(memberLevelService, never()).recalculateLevel("user001");
	}

	private UserInfo user(int point, int totalPoint) {
		return new UserInfo().setId("user001").setPoint(point).setTotalPoint(totalPoint);
	}

	/**
	 * 发放类流水按批次记账：带有效期配置时写入 expire_time 与 remain_point，
	 * 供 FIFO 消耗与到期清零；配置 0/缺省时 expire_time 为 NULL（永不过期）。
	 */
	@Test
	void incomeRecordCarriesExpireTimeAndRemainPoint() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500));
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
		when(userInfoMapper.acquirePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		when(pointsGlobalConfigService.getEffectiveConfig())
			.thenReturn(new PointsGlobalConfig().setValidityDays(365));

		service.recordPointsChange("user001", "1", 50, "ORDER_COMPLETE", "订单完成积分奖励");

		ArgumentCaptor<PointsRecord> captor = ArgumentCaptor.forClass(PointsRecord.class);
		verify(pointsRecordMapper).insert(captor.capture());
		org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue().getExpireTime());
		assertEquals(50, captor.getValue().getRemainPoint());
	}

	@Test
	void incomeRecordNeverExpiresWhenValidityDisabled() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500));
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
		when(userInfoMapper.acquirePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		when(pointsGlobalConfigService.getEffectiveConfig())
			.thenReturn(new PointsGlobalConfig().setValidityDays(0));

		service.recordPointsChange("user001", "1", 50, "ORDER_COMPLETE", "订单完成积分奖励");

		ArgumentCaptor<PointsRecord> captor = ArgumentCaptor.forClass(PointsRecord.class);
		verify(pointsRecordMapper).insert(captor.capture());
		org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getExpireTime());
	}

	/**
	 * FIFO 消耗：先扣最早到期批次、再扣较晚批次，各批次按条件 UPDATE 原子递减；
	 * 批次不足时按余额口径放行（存量未回填场景），不阻断消耗。
	 */
	@Test
	void consumeAllocatesRemainPointFifoByExpireTime() {
		when(userInfoMapper.selectById("user001")).thenReturn(user(100, 500));
		when(userInfoMapper.selectPointForUpdate("user001")).thenReturn(100);
		when(userInfoMapper.consumePoints("user001", 50)).thenReturn(1);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		PointsRecord early = new PointsRecord().setId("batch-early").setRemainPoint(30);
		PointsRecord late = new PointsRecord().setId("batch-late").setRemainPoint(50);
		when(pointsRecordMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(java.util.List.of(early, late));
		when(pointsRecordMapper.update(any(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(1);

		service.recordPointsChange("user001", "2", 50, "ORDER_DEDUCT", "积分抵现");

		// 30 + 20：两批都被递减，第二批只扣剩余 20
		verify(pointsRecordMapper, org.mockito.Mockito.times(2))
			.update(any(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
	}

	private static final class TestPointsRecordService extends PointsRecordServiceImpl {

		private TestPointsRecordService(UserInfoMapper userInfoMapper, IMemberLevelService memberLevelService,
				PointsRecordMapper pointsRecordMapper, IPointsGlobalConfigService pointsGlobalConfigService) {
			super(userInfoMapper, memberLevelService, pointsGlobalConfigService);
			this.baseMapper = pointsRecordMapper;
		}
	}

}
