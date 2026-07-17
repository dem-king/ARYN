package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

	private MemberBenefitServiceImpl memberBenefitService;

	@BeforeEach
	void setUp() {
		// 设置 baseMapper
		memberBenefitService = new TestMemberBenefitService(memberBenefitLevelRelMapper, memberBenefitMapper);
	}

	@Test
	@DisplayName("保存权益 - 正常保存")
	void saveBenefit_success() {
		// given
		MemberBenefit benefit = new MemberBenefit();
		benefit.setBenefitName("9折优惠");
		benefit.setBenefitType("1");
		benefit.setBenefitValue("0.9");
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
		benefit.setBenefitValue("0.8");
		when(memberBenefitMapper.updateById(any(MemberBenefit.class))).thenReturn(1);

		// when
		boolean result = memberBenefitService.updateBenefit(benefit);

		// then
		assertTrue(result);
		verify(memberBenefitMapper).updateById(benefit);
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
		when(memberBenefitMapper.selectByIds(anyCollection())).thenReturn(Arrays.asList(benefit1, benefit2));

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

	private static final class TestMemberBenefitService extends MemberBenefitServiceImpl {

		private TestMemberBenefitService(MemberBenefitLevelRelMapper memberBenefitLevelRelMapper,
				MemberBenefitMapper memberBenefitMapper) {
			super(memberBenefitLevelRelMapper);
			this.baseMapper = memberBenefitMapper;
		}
	}

}
