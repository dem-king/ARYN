package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitMapper;
import com.aryn.cloud.user.service.IMemberBenefitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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

	@Override
	public IPage<MemberBenefit> getPage(Page page, MemberBenefit memberBenefit) {
		return this.page(page,
				Wrappers.<MemberBenefit>lambdaQuery().orderByDesc(MemberBenefit::getCreateTime));
	}

	@Override
	public boolean saveBenefit(MemberBenefit memberBenefit) {
		return this.save(memberBenefit);
	}

	@Override
	public boolean updateBenefit(MemberBenefit memberBenefit) {
		return this.updateById(memberBenefit);
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
		// 先删除旧关联
		memberBenefitLevelRelMapper.delete(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getBenefitId, benefitId));

		// 批量插入新关联
		List<MemberBenefitLevelRel> rels = new ArrayList<>();
		for (String levelId : levelIds) {
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
		List<MemberBenefitLevelRel> rels = memberBenefitLevelRelMapper.selectList(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getLevelId, levelId));
		if (rels.isEmpty()) {
			return new ArrayList<>();
		}
		List<String> benefitIds = rels.stream().map(MemberBenefitLevelRel::getBenefitId).collect(Collectors.toList());
		return this.listByIds(benefitIds);
	}

}
