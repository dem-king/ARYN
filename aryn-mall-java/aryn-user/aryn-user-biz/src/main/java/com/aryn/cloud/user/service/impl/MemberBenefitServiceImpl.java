package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitMapper;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.service.IMemberBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员权益
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberBenefitServiceImpl extends ServiceImpl<MemberBenefitMapper, MemberBenefit>
		implements IMemberBenefitService {

	private final MemberBenefitLevelRelMapper memberBenefitLevelRelMapper;

	private final MemberLevelMapper memberLevelMapper;

	private final UserInfoMapper userInfoMapper;

	@Override
	public IPage<MemberBenefit> getPage(Page page, MemberBenefit memberBenefit) {
		return this.page(page,
				Wrappers.<MemberBenefit>lambdaQuery().orderByDesc(MemberBenefit::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveBenefit(MemberBenefit memberBenefit) {
		validateBenefit(memberBenefit);
		clearSystemFields(memberBenefit);
		if (!this.save(memberBenefit)) {
			return false;
		}
		bindLevels(memberBenefit.getId(), memberBenefit.getLevelIds());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateBenefit(MemberBenefit memberBenefit) {
		validateBenefit(memberBenefit);
		clearSystemFields(memberBenefit);
		if (!this.updateById(memberBenefit)) {
			return false;
		}
		bindLevels(memberBenefit.getId(), memberBenefit.getLevelIds());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean deleteBenefit(String id) {
		// 删除等级关联关系
		memberBenefitLevelRelMapper.delete(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getBenefitId, id));
		return this.removeById(id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void bindLevels(String benefitId, List<String> levelIds) {
		if (this.getById(benefitId) == null) {
			throw new ArynBusinessException("会员权益不存在");
		}
		Set<String> uniqueLevelIds = levelIds == null ? Set.of() : new LinkedHashSet<>(levelIds);
		if (uniqueLevelIds.stream().anyMatch(levelId -> levelId == null || levelId.isBlank())) {
			throw new ArynBusinessException("会员等级ID不能为空");
		}
		if (!uniqueLevelIds.isEmpty() && memberLevelMapper.selectByIds(uniqueLevelIds).size() != uniqueLevelIds.size()) {
			throw new ArynBusinessException("会员等级不存在");
		}
		// 先删除旧关联
		memberBenefitLevelRelMapper.delete(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getBenefitId, benefitId));

		// 批量插入新关联
		List<MemberBenefitLevelRel> rels = new ArrayList<>();
		for (String levelId : uniqueLevelIds) {
			MemberBenefitLevelRel rel = new MemberBenefitLevelRel();
			rel.setBenefitId(benefitId);
			rel.setLevelId(levelId);
			rels.add(rel);
		}
		if (!rels.isEmpty()) {
			memberBenefitLevelRelMapper.insert(rels);
		}
	}

	@Override
	public List<MemberBenefit> getLevelBenefits(String levelId) {
		if (levelId == null || memberLevelMapper.selectById(levelId) == null) {
			return List.of();
		}
		List<MemberBenefitLevelRel> rels = memberBenefitLevelRelMapper.selectList(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getLevelId, levelId));
		if (rels.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> benefitIds = rels.stream().map(MemberBenefitLevelRel::getBenefitId).collect(Collectors.toList());
		return this.list(Wrappers.<MemberBenefit>lambdaQuery()
				.in(MemberBenefit::getId, benefitIds)
				.eq(MemberBenefit::getStatus, "0"));
	}

	@Override
	public List<MemberBenefit> getEnabledLevelBenefits(String levelId) {
		MemberLevel level = levelId == null ? null : memberLevelMapper.selectById(levelId);
		if (level == null || !"0".equals(level.getStatus())) {
			return List.of();
		}
		return getLevelBenefits(levelId);
	}

	@Override
	public List<MemberLevel> getEnabledLevels() {
		return memberLevelMapper.selectList(Wrappers.<MemberLevel>lambdaQuery()
				.eq(MemberLevel::getStatus, "0").orderByAsc(MemberLevel::getSortOrder));
	}

	@Override
	public MemberBenefitsVO getUserBenefits(String userId) {
		MemberBenefitsVO result = new MemberBenefitsVO();
		UserInfo user = userInfoMapper.selectById(userId);
		if (user == null || user.getMemberLevelId() == null) {
			return result;
		}
		MemberLevel level = memberLevelMapper.selectById(user.getMemberLevelId());
		if (level == null || !"0".equals(level.getStatus())) {
			return result;
		}
		result.setLevelId(level.getId());
		result.setLevelName(level.getLevelName());
		for (MemberBenefit benefit : getLevelBenefits(level.getId())) {
			validateBenefit(benefit);
			switch (benefit.getBenefitType()) {
				case "1" -> result.setDiscountRate(result.getDiscountRate()
						.min(new BigDecimal(benefit.getBenefitValue())));
				case "2" -> result.setFreeShipping(true);
				case "3" -> result.getExclusiveCouponTemplateIds().add(benefit.getBenefitValue());
				case "4" -> result.setPointsMultiplier(result.getPointsMultiplier()
						.max(new BigDecimal(benefit.getBenefitValue())));
				default -> throw new ArynBusinessException("会员权益类型不合法");
			}
		}
		return result;
	}

	@Override
	public MemberBenefit getBenefitDetail(String id) {
		MemberBenefit benefit = this.getById(id);
		if (benefit == null) {
			return null;
		}
		benefit.setLevelIds(memberBenefitLevelRelMapper.selectList(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getBenefitId, id))
				.stream().map(MemberBenefitLevelRel::getLevelId).toList());
		return benefit;
	}

	private void validateBenefit(MemberBenefit benefit) {
		if (benefit == null || benefit.getBenefitName() == null || benefit.getBenefitName().isBlank()) {
			throw new ArynBusinessException("会员权益名称不能为空");
		}
		if (benefit.getStatus() != null && !Set.of("0", "1").contains(benefit.getStatus())) {
			throw new ArynBusinessException("会员权益状态不合法");
		}
		if (benefit.getBenefitType() == null
				|| !Set.of("1", "2", "3", "4").contains(benefit.getBenefitType())) {
			throw new ArynBusinessException("会员权益类型不合法");
		}
		if (benefit.getBenefitValue() == null || benefit.getBenefitValue().isBlank()) {
			throw new ArynBusinessException("会员权益值不能为空");
		}
		if ("3".equals(benefit.getBenefitType())) {
			return;
		}
		try {
			BigDecimal value = new BigDecimal(benefit.getBenefitValue());
			if ("1".equals(benefit.getBenefitType())
					&& (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) > 0)) {
				throw new ArynBusinessException("会员折扣必须大于0且不大于1");
			}
			if ("2".equals(benefit.getBenefitType()) && value.compareTo(BigDecimal.ONE) != 0) {
				throw new ArynBusinessException("免邮权益值必须为1");
			}
			if ("4".equals(benefit.getBenefitType()) && value.compareTo(BigDecimal.ONE) < 0) {
				throw new ArynBusinessException("积分倍率不能小于1");
			}
		}
		catch (NumberFormatException exception) {
			throw new ArynBusinessException("会员权益值格式不合法");
		}
	}

	private void clearSystemFields(MemberBenefit benefit) {
		benefit.setTenantId(null);
		benefit.setCreateBy(null);
		benefit.setUpdateBy(null);
		benefit.setCreateTime(null);
		benefit.setUpdateTime(null);
		benefit.setDelFlag(null);
	}

}
