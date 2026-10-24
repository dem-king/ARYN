package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.remote.RemoteCouponInfoService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.api.vo.MemberBenefitBackfillResultVO;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitMapper;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberTagService;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemberBenefitServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MemberBenefitServiceImplTest {

	@Mock
	private MemberBenefitLevelRelMapper memberBenefitLevelRelMapper;

	@Mock
	private MemberBenefitMapper memberBenefitMapper;

	@Mock private MemberLevelMapper memberLevelMapper;
	@Mock private UserInfoMapper userInfoMapper;
	@Mock private IMemberTagService memberTagService;
	@Mock private RemoteCouponInfoService remoteCouponInfoService;
	@Mock private RemoteCouponUserService remoteCouponUserService;

	private MemberBenefitServiceImpl memberBenefitService;

	@BeforeEach
	void setUp() {
		// 设置 baseMapper
		memberBenefitService = new TestMemberBenefitService(memberBenefitLevelRelMapper, memberLevelMapper,
				userInfoMapper, memberTagService, remoteCouponInfoService, remoteCouponUserService, memberBenefitMapper);
		lenient().when(memberBenefitMapper.selectById(any())).thenReturn(new MemberBenefit().setId("benefit001"));
		lenient().when(memberLevelMapper.selectById(any())).thenReturn(new MemberLevel().setId("level001"));
		lenient().when(memberLevelMapper.selectByIds(anyCollection())).thenAnswer(invocation ->
				((Collection<?>) invocation.getArgument(0)).stream().map(id -> new MemberLevel().setId(id.toString())).toList());
	}

	@Test
	@DisplayName("保存权益 - 正常保存")
	void saveBenefit_success() {
		// given
		MemberBenefit benefit = new MemberBenefit();
		benefit.setBenefitName("9折优惠");
		benefit.setBenefitType("1");
		benefit.setBenefitValue("0.9");
		benefit.setId("benefit001");
		benefit.setLevelIds(List.of("level001"));
		when(memberBenefitMapper.insert(any(MemberBenefit.class))).thenReturn(1);

		// when
		boolean result = memberBenefitService.saveBenefit(benefit);

		// then
		assertTrue(result);
		verify(memberBenefitMapper).insert(benefit);
	}

	@Test
	@DisplayName("更新权益 - 正常更新")
	void updateBenefit_success() {
		// given
		MemberBenefit benefit = new MemberBenefit();
		benefit.setId("benefit001");
		benefit.setBenefitName("8折优惠");
		benefit.setBenefitType("1");
		benefit.setBenefitValue("0.8");
		benefit.setLevelIds(List.of("level001"));
		when(memberBenefitMapper.updateById(any(MemberBenefit.class))).thenReturn(1);

		// when
		boolean result = memberBenefitService.updateBenefit(benefit);

		// then
		assertTrue(result);
		verify(memberBenefitMapper).updateById(benefit);
	}

	@Test
	@DisplayName("保存专属优惠券权益 - 模板有效时允许非数字模板ID")
	void saveExclusiveCouponBenefit_acceptsStringTemplateId() {
		MemberBenefit benefit = new MemberBenefit().setId("benefit001").setBenefitName("专属优惠券")
				.setBenefitType("3").setBenefitValue("coupon-template-uuid")
				.setLevelIds(List.of("level001"));
		when(remoteCouponInfoService.getTemplate("coupon-template-uuid")).thenReturn(couponTemplate("0", 10));
		when(memberBenefitMapper.insert(any(MemberBenefit.class))).thenReturn(1);

		assertTrue(memberBenefitService.saveBenefit(benefit));
	}

	@Test
	@DisplayName("保存权益 - 未关联等级时拒绝")
	void saveBenefit_withoutLevels_throws() {
		MemberBenefit benefit = new MemberBenefit().setId("benefit001").setBenefitName("9折优惠")
				.setBenefitType("1").setBenefitValue("0.9");

		assertThrows(ArynBusinessException.class, () -> memberBenefitService.saveBenefit(benefit));
		verify(memberBenefitMapper, never()).insert(any(MemberBenefit.class));
	}

	@Test
	@DisplayName("保存专属优惠券权益 - 模板不存在时拒绝")
	void saveExclusiveCouponBenefit_missingTemplate_throws() {
		MemberBenefit benefit = new MemberBenefit().setId("benefit001").setBenefitName("专属优惠券")
				.setBenefitType("3").setBenefitValue("missing-template")
				.setLevelIds(List.of("level001"));
		when(remoteCouponInfoService.getTemplate("missing-template")).thenReturn(null);

		assertThrows(ArynBusinessException.class, () -> memberBenefitService.saveBenefit(benefit));
		verify(memberBenefitMapper, never()).insert(any(MemberBenefit.class));
	}

	@Test
	@DisplayName("补发专属券 - 只补发给权益关联等级下的启用会员")
	void backfillCoupon_grantsUsersInBoundLevels() {
		when(memberBenefitMapper.selectById("benefit-coupon")).thenReturn(new MemberBenefit()
				.setId("benefit-coupon").setBenefitType("3").setBenefitValue("template-1"));
		when(remoteCouponInfoService.getTemplate("template-1")).thenReturn(couponTemplate("0", 10));
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(List.of(
				new MemberBenefitLevelRel().setBenefitId("benefit-coupon").setLevelId("level001")));
		Page<UserInfo> userPage = new Page<>(1, 200, false);
		userPage.setRecords(List.of(
				new UserInfo().setId("user001").setStatus("0"),
				new UserInfo().setId("user002").setStatus("0")));
		when(userInfoMapper.selectPage(any(), any())).thenReturn(userPage);
		when(remoteCouponUserService.grantMemberBenefitCoupon("template-1", "user001", "level001:benefit-coupon"))
			.thenReturn(true);
		when(remoteCouponUserService.grantMemberBenefitCoupon("template-1", "user002", "level001:benefit-coupon"))
			.thenReturn(true);

		MemberBenefitBackfillResultVO result = memberBenefitService.backfillCoupon("benefit-coupon");

		assertEquals(1, result.getLevelCount());
		assertEquals(2, result.getUserCount());
		assertEquals(2, result.getSuccessCount());
		assertEquals(0, result.getFailedCount());
	}

	@Test
	@DisplayName("补发专属券 - 单个会员失败不回滚整批")
	void backfillCoupon_continuesWhenSingleGrantFails() {
		when(memberBenefitMapper.selectById("benefit-coupon")).thenReturn(new MemberBenefit()
				.setId("benefit-coupon").setBenefitType("3").setBenefitValue("template-1"));
		when(remoteCouponInfoService.getTemplate("template-1")).thenReturn(couponTemplate("0", 10));
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(List.of(
				new MemberBenefitLevelRel().setBenefitId("benefit-coupon").setLevelId("level001")));
		Page<UserInfo> userPage = new Page<>(1, 200, false);
		userPage.setRecords(List.of(
				new UserInfo().setId("user001").setStatus("0"),
				new UserInfo().setId("user002").setStatus("0")));
		when(userInfoMapper.selectPage(any(), any())).thenReturn(userPage);
		when(remoteCouponUserService.grantMemberBenefitCoupon(eq("template-1"), anyString(), anyString()))
			.thenReturn(true)
			.thenThrow(new RuntimeException("库存不足"));

		MemberBenefitBackfillResultVO result = memberBenefitService.backfillCoupon("benefit-coupon");

		assertEquals(2, result.getUserCount());
		assertEquals(1, result.getSuccessCount());
		assertEquals(1, result.getFailedCount());
	}

	@Test
	@DisplayName("删除权益 - 级联删除等级关联关系")
	void deleteBenefit_cascadeDeleteLevelRels() {
		// given
		when(memberBenefitLevelRelMapper.delete(any())).thenReturn(2);
		when(memberBenefitMapper.deleteById("benefit001")).thenReturn(1);

		// when
		boolean result = memberBenefitService.deleteBenefit("benefit001");

		// then
		assertTrue(result);
		// 先删除等级关联
		verify(memberBenefitLevelRelMapper).delete(any());
		// 再删除权益本身
		verify(memberBenefitMapper).deleteById("benefit001");
	}

	@Test
	@DisplayName("绑定等级 - 先删除旧关联再批量插入新关联")
	void bindLevels_deleteOldAndInsertNew() {
		// given
		String benefitId = "benefit001";
		List<String> levelIds = Arrays.asList("level001", "level002");

		when(memberBenefitLevelRelMapper.delete(any())).thenReturn(1);
		when(memberBenefitLevelRelMapper.insert(anyList())).thenReturn(Collections.emptyList());

		// when
		memberBenefitService.bindLevels(benefitId, levelIds);

		// then
		// 先删除旧关联
		verify(memberBenefitLevelRelMapper).delete(any());
		// 再插入新关联
		verify(memberBenefitLevelRelMapper).insert(argThat((Collection<MemberBenefitLevelRel> rels) -> {
			return rels.size() == 2
					&& rels.stream().allMatch(r -> benefitId.equals(r.getBenefitId()))
					&& rels.stream().map(MemberBenefitLevelRel::getLevelId)
					.collect(java.util.stream.Collectors.toList())
					.containsAll(levelIds);
		}));
	}

	@Test
	@DisplayName("绑定等级 - 空等级列表时只删除不插入")
	void bindLevels_emptyLevelIds_onlyDelete() {
		// given
		String benefitId = "benefit001";
		List<String> levelIds = Collections.emptyList();

		when(memberBenefitLevelRelMapper.delete(any())).thenReturn(2);

		// when
		memberBenefitService.bindLevels(benefitId, levelIds);

		// then
		verify(memberBenefitLevelRelMapper).delete(any());
		verify(memberBenefitLevelRelMapper, never()).insert(anyList());
	}

	@Test
	@DisplayName("绑定等级 - 替换等级关联(先删后插)")
	void bindLevels_replaceLevels() {
		// given
		String benefitId = "benefit001";
		// 原来绑定level001，现在改为绑定level002和level003
		List<String> newLevelIds = Arrays.asList("level002", "level003");

		when(memberBenefitLevelRelMapper.delete(any())).thenReturn(1);
		when(memberBenefitLevelRelMapper.insert(anyList())).thenReturn(Collections.emptyList());

		// when
		memberBenefitService.bindLevels(benefitId, newLevelIds);

		// then
		verify(memberBenefitLevelRelMapper).delete(any());
		verify(memberBenefitLevelRelMapper).insert(argThat((Collection<MemberBenefitLevelRel> rels) -> {
			return rels.size() == 2;
		}));
	}

	@Test
	@DisplayName("获取等级权益 - 等级有权益时返回权益列表")
	void getLevelBenefits_hasBenefits() {
		// given
		String levelId = "level001";

		MemberBenefitLevelRel rel1 = new MemberBenefitLevelRel();
		rel1.setBenefitId("benefit001");
		rel1.setLevelId(levelId);
		MemberBenefitLevelRel rel2 = new MemberBenefitLevelRel();
		rel2.setBenefitId("benefit002");
		rel2.setLevelId(levelId);
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(Arrays.asList(rel1, rel2));

		MemberBenefit benefit1 = new MemberBenefit();
		benefit1.setId("benefit001");
		benefit1.setBenefitName("9折优惠");
		MemberBenefit benefit2 = new MemberBenefit();
		benefit2.setId("benefit002");
		benefit2.setBenefitName("免运费");
		when(memberBenefitMapper.selectList(any())).thenReturn(Arrays.asList(benefit1, benefit2));

		// when
		List<MemberBenefit> result = memberBenefitService.getLevelBenefits(levelId);

		// then
		assertEquals(2, result.size());
	}

	@Test
	@DisplayName("获取等级权益 - 等级无权益时返回空列表")
	void getLevelBenefits_noBenefits() {
		// given
		String levelId = "level001";
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(Collections.emptyList());

		// when
		List<MemberBenefit> result = memberBenefitService.getLevelBenefits(levelId);

		// then
		assertTrue(result.isEmpty());
		verify(memberBenefitMapper, never()).selectByIds(anyCollection());
	}

	@Test
	@DisplayName("C端获取等级权益 - 禁用等级不返回权益")
	void getEnabledLevelBenefits_disabledLevelReturnsEmpty() {
		when(memberLevelMapper.selectById("level-disabled"))
				.thenReturn(new MemberLevel().setId("level-disabled").setStatus("1"));

		assertTrue(memberBenefitService.getEnabledLevelBenefits("level-disabled").isEmpty());
		verify(memberBenefitLevelRelMapper, never()).selectList(any());
	}

	@Test
	@DisplayName("聚合用户权益 - 折扣和积分倍率取最优值且免邮和专属券生效")
	void getUserBenefits_aggregatesBestBenefits() {
		String levelId = "level001";
		when(userInfoMapper.selectById("user001"))
				.thenReturn(new UserInfo().setId("user001").setMemberLevelId(levelId));
		when(memberLevelMapper.selectById(levelId))
				.thenReturn(new MemberLevel().setId(levelId).setLevelName("银卡").setStatus("0"));
		List<MemberBenefit> benefits = List.of(
				benefit("discount-1", "1", "0.9"),
				benefit("discount-2", "1", "0.8"),
				benefit("shipping", "2", "1"),
				benefit("coupon", "3", "coupon-template-uuid"),
				benefit("points-1", "4", "2"),
				benefit("points-2", "4", "3"),
				benefit("ratio-1", "5", "30"),
				benefit("ratio-2", "5", "50"));
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(benefits.stream()
				.map(benefit -> new MemberBenefitLevelRel().setBenefitId(benefit.getId()).setLevelId(levelId))
				.toList());
		when(memberBenefitMapper.selectList(any())).thenReturn(benefits);

		MemberBenefitsVO result = memberBenefitService.getUserBenefits("user001");

		assertEquals(new BigDecimal("0.8"), result.getDiscountRate());
		assertTrue(result.isFreeShipping());
		assertEquals(new BigDecimal("3"), result.getPointsMultiplier());
		assertEquals(List.of("coupon-template-uuid"), result.getExclusiveCouponTemplateIds());
		// 等级专属抵扣上限：多档取最大（等级越高抵得越多），与积分倍率同口径
		assertEquals(new BigDecimal("50"), result.getDeductMaxRatio());
	}

	@Test
	@DisplayName("聚合用户权益 - 未配置抵扣上限时 deductMaxRatio 为 null（回落全局配置）")
	void getUserBenefits_deductRatioNullWhenNotConfigured() {
		String levelId = "level001";
		when(userInfoMapper.selectById("user001"))
				.thenReturn(new UserInfo().setId("user001").setMemberLevelId(levelId));
		when(memberLevelMapper.selectById(levelId))
				.thenReturn(new MemberLevel().setId(levelId).setLevelName("银卡").setStatus("0"));
		List<MemberBenefit> benefits = List.of(benefit("discount-1", "1", "0.9"));
		when(memberBenefitLevelRelMapper.selectList(any())).thenReturn(benefits.stream()
				.map(benefit -> new MemberBenefitLevelRel().setBenefitId(benefit.getId()).setLevelId(levelId))
				.toList());
		when(memberBenefitMapper.selectList(any())).thenReturn(benefits);

		MemberBenefitsVO result = memberBenefitService.getUserBenefits("user001");

		assertNull(result.getDeductMaxRatio());
	}

	@Test
	@DisplayName("保存权益 - 抵扣上限必须在 (0, 100]")
	void saveBenefit_deductRatioOutOfRangeRejected() {
		MemberBenefit benefit = new MemberBenefit();
		benefit.setBenefitName("白金抵扣上限");
		benefit.setBenefitType("5");
		benefit.setBenefitValue("120");
		benefit.setLevelIds(List.of("level001"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> memberBenefitService.saveBenefit(benefit));
		assertEquals("积分抵扣上限必须大于0且不大于100（百分数）", exception.getMsg());
	}

	@Test
	@DisplayName("删除权益 - 确保删除顺序：先删关联再删权益")
	void deleteBenefit_deleteOrder() {
		// given
		when(memberBenefitLevelRelMapper.delete(any())).thenReturn(1);
		when(memberBenefitMapper.deleteById("benefit001")).thenReturn(1);

		// when
		memberBenefitService.deleteBenefit("benefit001");

		// then - 验证调用顺序
		inOrder(memberBenefitLevelRelMapper, memberBenefitMapper).verify(memberBenefitLevelRelMapper).delete(any());
		inOrder(memberBenefitLevelRelMapper, memberBenefitMapper).verify(memberBenefitMapper).deleteById("benefit001");
	}

	private static MemberBenefit benefit(String id, String type, String value) {
		return new MemberBenefit().setId(id).setBenefitName(id)
				.setBenefitType(type).setBenefitValue(value).setStatus("0");
	}

	private static CouponTemplateInfoVO couponTemplate(String status, int remainNum) {
		CouponTemplateInfoVO vo = new CouponTemplateInfoVO();
		vo.setId("coupon-template-uuid");
		vo.setStatus(status);
		vo.setRemainNum(remainNum);
		vo.setReceiveEndedAt(java.time.LocalDateTime.now().plusDays(1));
		return vo;
	}

	private static final class TestMemberBenefitService extends MemberBenefitServiceImpl {

		private TestMemberBenefitService(MemberBenefitLevelRelMapper memberBenefitLevelRelMapper,
				MemberLevelMapper memberLevelMapper, UserInfoMapper userInfoMapper, IMemberTagService memberTagService,
				RemoteCouponInfoService remoteCouponInfoService, RemoteCouponUserService remoteCouponUserService,
				MemberBenefitMapper memberBenefitMapper) {
			super(memberBenefitLevelRelMapper, memberLevelMapper, userInfoMapper, memberTagService,
					remoteCouponInfoService, remoteCouponUserService);
			this.baseMapper = memberBenefitMapper;
		}
	}

}
