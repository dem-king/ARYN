package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberLevelRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.MemberLevelRecordMapper;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberBenefitService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemberLevelServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MemberLevelServiceImplTest {

	@Mock
	private MemberLevelRecordMapper memberLevelRecordMapper;

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private MemberLevelMapper memberLevelMapper;

	@Mock
	private MemberBenefitLevelRelMapper memberBenefitLevelRelMapper;

	@Mock private IMemberBenefitService memberBenefitService;
	@Mock private RemoteCouponUserService remoteCouponUserService;

	private MemberLevelServiceImpl memberLevelService;

	private UserInfo testUser;

	private MemberLevel bronzeLevel;
	private MemberLevel silverLevel;
	private MemberLevel goldLevel;

	@BeforeEach
	void setUp() {
		testUser = new UserInfo();
		testUser.setId("user001");
		testUser.setPoint(500);
		testUser.setTotalPoint(500);
		testUser.setBalance(BigDecimal.ZERO);
		testUser.setTotalConsume(new BigDecimal("1000.00"));
		testUser.setMemberLevelId(null);

		bronzeLevel = new MemberLevel();
		bronzeLevel.setId("level-bronze");
		bronzeLevel.setLevelName("青铜会员");
		bronzeLevel.setConditionType("1"); // 累计消费金额
		bronzeLevel.setConditionValue(new BigDecimal("100.00"));
		bronzeLevel.setStatus("0");
		bronzeLevel.setSortOrder(1);

		silverLevel = new MemberLevel();
		silverLevel.setId("level-silver");
		silverLevel.setLevelName("白银会员");
		silverLevel.setConditionType("1");
		silverLevel.setConditionValue(new BigDecimal("500.00"));
		silverLevel.setStatus("0");
		silverLevel.setSortOrder(2);

		goldLevel = new MemberLevel();
		goldLevel.setId("level-gold");
		goldLevel.setLevelName("黄金会员");
		goldLevel.setConditionType("2"); // 累计积分
		goldLevel.setConditionValue(new BigDecimal("1000"));
		goldLevel.setStatus("0");
		goldLevel.setSortOrder(3);

		// 设置 baseMapper
		memberLevelService = new TestMemberLevelService(memberLevelRecordMapper, userInfoMapper,
				memberBenefitLevelRelMapper, memberBenefitService, remoteCouponUserService, memberLevelMapper);
		lenient().when(userInfoMapper.updateMemberLevel(anyString(), nullable(String.class))).thenReturn(1);
	}

	@Test
	@DisplayName("等级重算 - 按累计消费金额匹配最高等级")
	void recalculateLevel_matchByTotalConsume() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		// 模拟 this.list() 返回启用的等级列表
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel));

		// when
		memberLevelService.recalculateLevel("user001");

		// then - 用户消费1000，满足青铜(100)和白银(500)，取最高白银
		verify(userInfoMapper).updateMemberLevel("user001", "level-silver");
		verify(memberLevelRecordMapper).insert(argThat((MemberLevelRecord record) ->
				"user001".equals(record.getUserId())
						&& record.getOldLevelId() == null
						&& "level-silver".equals(record.getNewLevelId())
						&& "系统自动计算".equals(record.getChangeReason())));
	}

	@Test
	@DisplayName("等级重算 - 按累计积分匹配等级")
	void recalculateLevel_matchByPoints() {
		// given
		testUser.setTotalConsume(new BigDecimal("50.00")); // 消费不满足任何等级
		testUser.setPoint(10); // 可用积分不参与等级成长
		testUser.setTotalPoint(1500); // 累计积分满足黄金等级
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel, goldLevel));

		// when
		memberLevelService.recalculateLevel("user001");

		// then - 积分1500满足黄金(1000)，消费50不满足青铜(100)和白银(500)
		verify(userInfoMapper).updateMemberLevel("user001", "level-gold");
		verify(memberLevelRecordMapper).insert(argThat((MemberLevelRecord record) ->
				"level-gold".equals(record.getNewLevelId())));
	}

	@Test
	@DisplayName("等级重算 - 用户不存在时直接返回")
	void recalculateLevel_userNotFound() {
		// given
		when(userInfoMapper.selectById("user999")).thenReturn(null);

		// when
		memberLevelService.recalculateLevel("user999");

		// then - 不做任何操作
		verify(userInfoMapper, never()).updateMemberLevel(anyString(), any());
		verify(memberLevelRecordMapper, never()).insert(any(MemberLevelRecord.class));
	}

	@Test
	@DisplayName("等级重算 - 没有启用的等级配置时直接返回")
	void recalculateLevel_noEnabledLevels() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Collections.emptyList());

		// when
		memberLevelService.recalculateLevel("user001");

		// then
		verify(userInfoMapper, never()).updateMemberLevel(anyString(), any());
		verify(memberLevelRecordMapper, never()).insert(any(MemberLevelRecord.class));
	}

	@Test
	@DisplayName("等级重算 - 等级未变化时不更新")
	void recalculateLevel_levelUnchanged() {
		// given
		testUser.setMemberLevelId("level-silver"); // 已经是白银
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel));

		// when
		memberLevelService.recalculateLevel("user001");

		// then - 等级没变，不更新
		verify(userInfoMapper, never()).updateMemberLevel(anyString(), any());
		verify(memberLevelRecordMapper, never()).insert(any(MemberLevelRecord.class));
	}

	@Test
	@DisplayName("等级重算 - 等级升级时记录变更")
	void recalculateLevel_levelUpgrade() {
		// given
		testUser.setMemberLevelId("level-bronze"); // 当前青铜
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel));

		// when
		memberLevelService.recalculateLevel("user001");

		// then - 升级到白银
		verify(userInfoMapper).updateMemberLevel("user001", "level-silver");
		verify(memberLevelRecordMapper).insert(argThat((MemberLevelRecord record) ->
				"level-bronze".equals(record.getOldLevelId())
						&& "level-silver".equals(record.getNewLevelId())));
	}

	@Test
	@DisplayName("等级重算 - 不满足任何等级条件时等级设为null")
	void recalculateLevel_noMatchedLevel() {
		// given
		testUser.setTotalConsume(new BigDecimal("50.00")); // 不满足青铜(100)
		testUser.setPoint(10); // 不满足黄金(1000)
		testUser.setTotalPoint(10);
		testUser.setMemberLevelId("level-bronze"); // 当前有等级
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel, goldLevel));

		// when
		memberLevelService.recalculateLevel("user001");

		// then - 等级降为null
		verify(userInfoMapper).updateMemberLevel("user001", null);
		verify(memberLevelRecordMapper).insert(argThat((MemberLevelRecord record) ->
				"level-bronze".equals(record.getOldLevelId())
						&& record.getNewLevelId() == null));
	}

	@Test
	@DisplayName("等级重算 - 从无等级升级到有等级")
	void recalculateLevel_fromNullToLevel() {
		// given
		testUser.setMemberLevelId(null);
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(memberLevelMapper.selectList(any())).thenReturn(Arrays.asList(bronzeLevel, silverLevel));
		MemberBenefit coupon = new MemberBenefit().setId("benefit-1").setBenefitType("3").setBenefitValue("template-1");
		when(memberBenefitService.getLevelBenefits("level-silver")).thenReturn(List.of(coupon));

		// when
		memberLevelService.recalculateLevel("user001");

		// then
		verify(userInfoMapper).updateMemberLevel("user001", "level-silver");
		verify(memberLevelRecordMapper).insert(argThat((MemberLevelRecord record) ->
				record.getOldLevelId() == null
						&& "level-silver".equals(record.getNewLevelId())));
		verify(remoteCouponUserService).grantMemberBenefitCoupon(
				"template-1", "user001", "level-silver:benefit-1");
	}

	@Test
	@DisplayName("删除等级 - 等级下存在会员时抛出异常")
	void deleteLevel_hasUsers() {
		// given
		when(userInfoMapper.selectCount(any())).thenReturn(3L);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				memberLevelService.deleteLevel("level-bronze"));

		assertEquals("该等级下存在会员，不允许删除", exception.getMsg());
		verify(memberLevelMapper, never()).deleteById(anyString());
	}

	@Test
	@DisplayName("删除等级 - 等级下无会员时正常删除")
	void deleteLevel_noUsers() {
		// given
		when(userInfoMapper.selectCount(any())).thenReturn(0L);
		when(memberLevelMapper.deleteById("level-bronze")).thenReturn(1);

		// when
		boolean result = memberLevelService.deleteLevel("level-bronze");

		// then
		assertTrue(result);
		verify(memberLevelMapper).deleteById("level-bronze");
		verify(memberBenefitLevelRelMapper).delete(any());
	}

	@Test
	@DisplayName("保存等级 - 升级条件值重复时抛出异常")
	void saveLevel_duplicateConditionValue() {
		// given
		MemberLevel newLevel = new MemberLevel();
		newLevel.setLevelName("重复等级");
		newLevel.setConditionType("1");
		newLevel.setConditionValue(new BigDecimal("100.00"));
		newLevel.setSortOrder(4);
		when(memberLevelMapper.selectCount(any())).thenReturn(1L);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				memberLevelService.saveLevel(newLevel));

		assertEquals("相同升级条件类型的条件值已存在", exception.getMsg());
	}

	@Test
	@DisplayName("保存等级 - 条件值不重复时正常保存")
	void saveLevel_success() {
		// given
		MemberLevel newLevel = new MemberLevel();
		newLevel.setLevelName("新等级");
		newLevel.setConditionType("1");
		newLevel.setConditionValue(new BigDecimal("200.00"));
		newLevel.setSortOrder(4);
		when(memberLevelMapper.selectCount(any())).thenReturn(0L);
		when(memberLevelMapper.insert(any(MemberLevel.class))).thenReturn(1);

		// when
		boolean result = memberLevelService.saveLevel(newLevel);

		// then
		assertTrue(result);
		verify(memberLevelMapper).insert(newLevel);
	}

	private static final class TestMemberLevelService extends MemberLevelServiceImpl {

		private TestMemberLevelService(MemberLevelRecordMapper memberLevelRecordMapper, UserInfoMapper userInfoMapper,
				MemberBenefitLevelRelMapper memberBenefitLevelRelMapper, IMemberBenefitService memberBenefitService,
				RemoteCouponUserService remoteCouponUserService, MemberLevelMapper memberLevelMapper) {
			super(memberLevelRecordMapper, userInfoMapper, memberBenefitLevelRelMapper, memberBenefitService,
					remoteCouponUserService);
			this.baseMapper = memberLevelMapper;
		}
	}

}
