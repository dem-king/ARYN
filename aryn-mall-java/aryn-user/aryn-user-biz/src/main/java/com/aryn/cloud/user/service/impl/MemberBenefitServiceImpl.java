package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.api.entity.MemberBenefitLevelRel;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberTag;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberCurrentInfoVO;
import com.aryn.cloud.promotion.api.remote.RemoteCouponInfoService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;
import com.aryn.cloud.user.api.vo.MemberBenefitBackfillResultVO;
import com.aryn.cloud.user.mapper.MemberBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.MemberBenefitMapper;
import com.aryn.cloud.user.mapper.MemberLevelMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.service.IMemberBenefitService;
import com.aryn.cloud.user.service.IMemberTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

	private final IMemberTagService memberTagService;

	@DubboReference
	private final RemoteCouponInfoService remoteCouponInfoService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	@Override
	public IPage<MemberBenefit> getPage(Page page, MemberBenefit memberBenefit) {
		List<String> levelBenefitIds = null;
		if (memberBenefit != null && StrUtil.isNotBlank(memberBenefit.getLevelId())) {
			levelBenefitIds = memberBenefitLevelRelMapper.selectList(
					Wrappers.<MemberBenefitLevelRel>lambdaQuery()
						.eq(MemberBenefitLevelRel::getLevelId, memberBenefit.getLevelId()))
				.stream().map(MemberBenefitLevelRel::getBenefitId).distinct().toList();
			if (levelBenefitIds.isEmpty()) {
				return new Page<>(page.getCurrent(), page.getSize(), 0);
			}
		}
		IPage<MemberBenefit> result = this.page(page, Wrappers.<MemberBenefit>lambdaQuery()
				.like(memberBenefit != null && StrUtil.isNotBlank(memberBenefit.getBenefitName()),
						MemberBenefit::getBenefitName, memberBenefit == null ? null : memberBenefit.getBenefitName())
				.eq(memberBenefit != null && StrUtil.isNotBlank(memberBenefit.getBenefitType()),
						MemberBenefit::getBenefitType, memberBenefit == null ? null : memberBenefit.getBenefitType())
				.eq(memberBenefit != null && StrUtil.isNotBlank(memberBenefit.getStatus()),
						MemberBenefit::getStatus, memberBenefit == null ? null : memberBenefit.getStatus())
				.in(levelBenefitIds != null, MemberBenefit::getId, levelBenefitIds)
				.orderByDesc(MemberBenefit::getCreateTime));
		List<MemberBenefit> records = result.getRecords();
		if (!records.isEmpty()) {
			List<String> benefitIds = records.stream().map(MemberBenefit::getId).toList();
			Map<String, List<String>> levelMap = memberBenefitLevelRelMapper.selectList(
					Wrappers.<MemberBenefitLevelRel>lambdaQuery()
						.in(MemberBenefitLevelRel::getBenefitId, benefitIds))
				.stream().collect(Collectors.groupingBy(MemberBenefitLevelRel::getBenefitId,
						Collectors.mapping(MemberBenefitLevelRel::getLevelId, Collectors.toList())));
			records.forEach(benefit -> benefit.setLevelIds(levelMap.getOrDefault(benefit.getId(), List.of())));
		}
		return result;
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
			// 读取链路只做降级处理：单条脏权益不能拖垮整个「我的权益」接口
			if (!isUsableBenefit(benefit)) {
				log.warn("会员权益配置不合法，已跳过：benefitId={}, benefitType={}, benefitValue={}",
						benefit.getId(), benefit.getBenefitType(), benefit.getBenefitValue());
				continue;
			}
			switch (benefit.getBenefitType()) {
				case "1" -> result.setDiscountRate(result.getDiscountRate()
						.min(new BigDecimal(benefit.getBenefitValue())));
				case "2" -> result.setFreeShipping(true);
				case "3" -> result.getExclusiveCouponTemplateIds().add(benefit.getBenefitValue());
				case "4" -> result.setPointsMultiplier(result.getPointsMultiplier()
						.max(new BigDecimal(benefit.getBenefitValue())));
				default -> log.warn("会员权益类型未知，已跳过：benefitId={}, benefitType={}",
						benefit.getId(), benefit.getBenefitType());
			}
		}
		return result;
	}

	@Override
	public MemberCurrentInfoVO getCurrentMemberInfo(String userId) {
		MemberCurrentInfoVO result = new MemberCurrentInfoVO();
		UserInfo user = userInfoMapper.selectById(userId);
		if (user != null && user.getMemberLevelId() != null && !user.getMemberLevelId().isBlank()) {
			MemberLevel level = memberLevelMapper.selectById(user.getMemberLevelId());
			if (level != null) {
				result.setLevelId(level.getId());
				result.setLevelName(level.getLevelName());
			}
		}
		List<MemberCurrentInfoVO.TagItem> tagItems = memberTagService.getUserTags(userId).stream().map(tag -> {
			MemberCurrentInfoVO.TagItem item = new MemberCurrentInfoVO.TagItem();
			item.setTagId(tag.getId());
			item.setTagName(tag.getTagName());
			return item;
		}).collect(Collectors.toList());
		result.setTags(tagItems);
		return result;
	}

	@Override
	public MemberBenefitBackfillResultVO backfillCoupon(String benefitId) {
		MemberBenefit benefit = this.getById(benefitId);
		if (benefit == null) {
			throw new ArynBusinessException("会员权益不存在");
		}
		if (!"3".equals(benefit.getBenefitType())) {
			throw new ArynBusinessException("仅专属优惠券权益支持补发");
		}
		validateCouponTemplate(benefit.getBenefitValue());
		List<String> levelIds = memberBenefitLevelRelMapper.selectList(
				Wrappers.<MemberBenefitLevelRel>lambdaQuery().eq(MemberBenefitLevelRel::getBenefitId, benefitId))
			.stream().map(MemberBenefitLevelRel::getLevelId).distinct().toList();
		MemberBenefitBackfillResultVO result = new MemberBenefitBackfillResultVO();
		result.setLevelCount(levelIds.size());
		for (String levelId : levelIds) {
			long pageNo = 1;
			while (true) {
				IPage<UserInfo> users = userInfoMapper.selectPage(new Page<>(pageNo, 200, false),
						Wrappers.<UserInfo>lambdaQuery()
							.eq(UserInfo::getMemberLevelId, levelId)
							.eq(UserInfo::getStatus, "0"));
				for (UserInfo user : users.getRecords()) {
					result.setUserCount(result.getUserCount() + 1);
					try {
						remoteCouponUserService.grantMemberBenefitCoupon(
								benefit.getBenefitValue(), user.getId(), levelId + ":" + benefitId);
						result.setSuccessCount(result.getSuccessCount() + 1);
					}
					catch (Exception exception) {
						result.setFailedCount(result.getFailedCount() + 1);
						log.error("会员专属优惠券补发失败: userId={}, benefitId={}", user.getId(), benefitId, exception);
					}
				}
				if (users.getRecords().size() < 200) {
					break;
				}
				pageNo++;
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

	/**
	 * 判断权益配置是否可用于读取聚合（读取链路的降级判定，不抛异常）
	 */
	private boolean isUsableBenefit(MemberBenefit benefit) {
		if (benefit == null || benefit.getBenefitType() == null || benefit.getBenefitValue() == null
				|| benefit.getBenefitValue().isBlank()) {
			return false;
		}
		if (!Set.of("1", "2", "3", "4").contains(benefit.getBenefitType())) {
			return false;
		}
		if ("3".equals(benefit.getBenefitType())) {
			return true;
		}
		try {
			new BigDecimal(benefit.getBenefitValue());
			return true;
		}
		catch (NumberFormatException exception) {
			return false;
		}
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
		if (benefit.getLevelIds() == null || benefit.getLevelIds().isEmpty()) {
			throw new ArynBusinessException("请至少关联一个会员等级");
		}
		if ("3".equals(benefit.getBenefitType())) {
			validateCouponTemplate(benefit.getBenefitValue());
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

	private void validateCouponTemplate(String templateId) {
		CouponTemplateInfoVO template;
		try {
			template = remoteCouponInfoService.getTemplate(templateId);
		}
		catch (Exception exception) {
			log.warn("会员专属优惠券模板校验失败: templateId={}", templateId, exception);
			throw new ArynBusinessException("优惠券模板校验失败，请稍后重试");
		}
		if (template == null) {
			throw new ArynBusinessException("专属优惠券模板不存在");
		}
		if (!"0".equals(template.getStatus())) {
			throw new ArynBusinessException("专属优惠券模板已禁用");
		}
		if (template.getRemainNum() != null && template.getRemainNum() <= 0) {
			throw new ArynBusinessException("专属优惠券模板库存不足");
		}
		if (template.getReceiveEndedAt() != null && template.getReceiveEndedAt().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException("专属优惠券模板已过期");
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
