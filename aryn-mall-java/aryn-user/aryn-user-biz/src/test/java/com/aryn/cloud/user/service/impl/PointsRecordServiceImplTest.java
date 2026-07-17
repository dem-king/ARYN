package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PointsRecordServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class PointsRecordServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private IMemberLevelService memberLevelService;

	@Mock
	private PointsRecordMapper pointsRecordMapper;

	private PointsRecordServiceImpl pointsRecordService;

	private UserInfo testUser;

	@BeforeEach
	void setUp() {
		testUser = new UserInfo();
		testUser.setId("user001");
		testUser.setPoint(100);
		testUser.setBalance(java.math.BigDecimal.ZERO);
		testUser.setTotalConsume(java.math.BigDecimal.ZERO);

		// 设置 baseMapper，使 this.save() 等基类方法可用
		pointsRecordService = new TestPointsRecordService(userInfoMapper, memberLevelService, pointsRecordMapper);
	}

	@Test
	@DisplayName("获取积分 - 正常增加积分余额")
	void recordPointsChange_acquirePoints_success() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		// when
		pointsRecordService.recordPointsChange("user001", "1", 50, "ORDER_REWARD", "下单奖励");

		// then
		verify(userInfoMapper).updateById(argThat((UserInfo user) -> user.getPoint() == 150));
		verify(pointsRecordMapper).insert(argThat((PointsRecord record) ->
				"user001".equals(record.getUserId())
						&& "1".equals(record.getChangeType())
						&& record.getChangePoint() == 50
						&& record.getBalanceAfter() == 150
						&& "ORDER_REWARD".equals(record.getTriggerScene())
						&& "下单奖励".equals(record.getRemark())));
		verify(memberLevelService).recalculateLevel("user001");
	}

	@Test
	@DisplayName("消耗积分 - 正常减少积分余额")
	void recordPointsChange_consumePoints_success() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		// when
		pointsRecordService.recordPointsChange("user001", "2", 30, "EXCHANGE", "积分兑换");

		// then
		verify(userInfoMapper).updateById(argThat((UserInfo user) -> user.getPoint() == 70));
		verify(pointsRecordMapper).insert(argThat((PointsRecord record) ->
				"2".equals(record.getChangeType())
						&& record.getChangePoint() == 30
						&& record.getBalanceAfter() == 70));
		verify(memberLevelService).recalculateLevel("user001");
	}

	@Test
	@DisplayName("消耗积分 - 余额不足时抛出异常")
	void recordPointsChange_consumePoints_insufficientBalance() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				pointsRecordService.recordPointsChange("user001", "2", 200, "EXCHANGE", "积分兑换"));

		assertEquals("积分余额不足", exception.getMsg());
		verify(userInfoMapper, never()).updateById(any(UserInfo.class));
		verify(pointsRecordMapper, never()).insert(any(PointsRecord.class));
	}

	@Test
	@DisplayName("获取积分 - 用户不存在时抛出异常")
	void recordPointsChange_userNotFound() {
		// given
		when(userInfoMapper.selectById("user999")).thenReturn(null);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				pointsRecordService.recordPointsChange("user999", "1", 50, "ORDER_REWARD", "下单奖励"));

		assertEquals("用户不存在", exception.getMsg());
		verify(userInfoMapper, never()).updateById(any(UserInfo.class));
	}

	@Test
	@DisplayName("获取积分 - 用户积分为null时默认为0")
	void recordPointsChange_acquirePoints_nullPointDefaultsToZero() {
		// given
		testUser.setPoint(null);
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		// when
		pointsRecordService.recordPointsChange("user001", "1", 50, "MANUAL", "手动增加");

		// then
		verify(userInfoMapper).updateById(argThat((UserInfo user) -> user.getPoint() == 50));
		verify(pointsRecordMapper).insert(argThat((PointsRecord record) -> record.getBalanceAfter() == 50));
	}

	@Test
	@DisplayName("消耗积分 - 积分刚好等于消耗值时成功")
	void recordPointsChange_consumePoints_exactBalance() {
		// given
		testUser.setPoint(50);
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);

		// when
		pointsRecordService.recordPointsChange("user001", "2", 50, "EXCHANGE", "积分兑换");

		// then
		verify(userInfoMapper).updateById(argThat((UserInfo user) -> user.getPoint() == 0));
		verify(pointsRecordMapper).insert(argThat((PointsRecord record) -> record.getBalanceAfter() == 0));
	}

	@Test
	@DisplayName("等级重算失败时不影响积分变动主流程")
	void recordPointsChange_levelRecalculateFail_doesNotAffectMainFlow() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(pointsRecordMapper.insert(any(PointsRecord.class))).thenReturn(1);
		doThrow(new RuntimeException("等级服务异常")).when(memberLevelService).recalculateLevel("user001");

		// when & then - 不应抛出异常，主流程正常完成
		assertDoesNotThrow(() ->
				pointsRecordService.recordPointsChange("user001", "1", 50, "ORDER_REWARD", "下单奖励"));

		// 积分变动仍然生效
		verify(userInfoMapper).updateById(any(UserInfo.class));
		verify(pointsRecordMapper).insert(any(PointsRecord.class));
	}

	private static final class TestPointsRecordService extends PointsRecordServiceImpl {

		private TestPointsRecordService(UserInfoMapper userInfoMapper, IMemberLevelService memberLevelService,
				PointsRecordMapper pointsRecordMapper) {
			super(userInfoMapper, memberLevelService);
			this.baseMapper = pointsRecordMapper;
		}
	}

}
