package com.aryn.cloud.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.constant.EnterpriseMemberConstants;
import com.aryn.cloud.user.api.entity.EnterpriseBenefit;
import com.aryn.cloud.user.api.entity.EnterpriseBenefitLevelRel;
import com.aryn.cloud.user.api.entity.EnterpriseGrowth;
import com.aryn.cloud.user.api.entity.EnterpriseLevel;
import com.aryn.cloud.user.api.entity.EnterpriseLevelBinding;
import com.aryn.cloud.user.api.entity.EnterpriseLevelRecord;
import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;
import com.aryn.cloud.user.mapper.EnterpriseBenefitLevelRelMapper;
import com.aryn.cloud.user.mapper.EnterpriseBenefitMapper;
import com.aryn.cloud.user.mapper.EnterpriseGrowthMapper;
import com.aryn.cloud.user.mapper.EnterpriseLevelBindingMapper;
import com.aryn.cloud.user.mapper.EnterpriseLevelMapper;
import com.aryn.cloud.user.mapper.EnterpriseLevelRecordMapper;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 企业会员双轨服务实现（企业轨）。
 *
 * <p>等级重算口径：主体近 12 个自然月（含当月）已支付采购额求和，取满足
 * condition_value 门槛的最高档（sort_order 最大）；阈值下调/采购额下降会降级
 * （全量重算口径，与个人会员 recalculateLevel 一致）。绑定表单写方为本服务。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseMemberServiceImpl implements IEnterpriseMemberService {

	private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

	private static final int ROLLING_MONTHS = 12;

	private final EnterpriseLevelMapper enterpriseLevelMapper;

	private final EnterpriseBenefitMapper enterpriseBenefitMapper;

	private final EnterpriseBenefitLevelRelMapper enterpriseBenefitLevelRelMapper;

	private final EnterpriseLevelRecordMapper enterpriseLevelRecordMapper;

	private final EnterpriseGrowthMapper enterpriseGrowthMapper;

	private final EnterpriseLevelBindingMapper enterpriseLevelBindingMapper;

	// ------------------------------------------------------------------
	// 等级配置
	// ------------------------------------------------------------------

	@Override
	public List<EnterpriseLevel> listLevels(String tenantId, String status, String keyword) {
		return enterpriseLevelMapper.selectList(Wrappers.lambdaQuery(EnterpriseLevel.class)
			.eq(EnterpriseLevel::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), EnterpriseLevel::getStatus, status)
			.like(StringUtils.hasText(keyword), EnterpriseLevel::getLevelName, keyword)
			.orderByAsc(EnterpriseLevel::getSortOrder));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public EnterpriseLevel saveLevel(String tenantId, EnterpriseLevel level) {
		validateLevel(level);
		level.setId(null);
		level.setTenantId(tenantId);
		enterpriseLevelMapper.insert(level);
		return level;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public EnterpriseLevel updateLevel(String tenantId, String id, EnterpriseLevel level) {
		EnterpriseLevel existing = requireLevel(tenantId, id);
		validateLevel(level);
		level.setId(existing.getId());
		level.setTenantId(tenantId);
		enterpriseLevelMapper.updateById(level);
		return level;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteLevel(String tenantId, String id) {
		if (requireLevel(tenantId, id) == null) {
			return;
		}
		enterpriseLevelMapper.deleteById(id);
		List<EnterpriseBenefitLevelRel> rels = enterpriseBenefitLevelRelMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseBenefitLevelRel.class)
					.eq(EnterpriseBenefitLevelRel::getTenantId, tenantId)
					.eq(EnterpriseBenefitLevelRel::getLevelId, id));
		for (EnterpriseBenefitLevelRel rel : rels) {
			enterpriseBenefitLevelRelMapper.deleteById(rel.getId());
		}
		// 该等级主体回到未定级（绑定清空），下次重算按新配置重新定级
		List<EnterpriseLevelBinding> bindings = enterpriseLevelBindingMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseLevelBinding.class)
					.eq(EnterpriseLevelBinding::getTenantId, tenantId)
					.eq(EnterpriseLevelBinding::getLevelId, id));
		for (EnterpriseLevelBinding binding : bindings) {
			enterpriseLevelBindingMapper.upsert(IdUtil.getSnowflakeNextIdStr(), binding.getSubjectType(),
					binding.getSubjectId(), null, tenantId);
		}
	}

	private void validateLevel(EnterpriseLevel level) {
		if (!StringUtils.hasText(level.getLevelName())) {
			throw new ArynBusinessException("等级名称不能为空");
		}
		if (level.getConditionValue() == null || level.getConditionValue().compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("升级门槛不合法");
		}
		if (level.getSortOrder() == null) {
			throw new ArynBusinessException("排序号不能为空");
		}
	}

	private EnterpriseLevel requireLevel(String tenantId, String id) {
		EnterpriseLevel level = enterpriseLevelMapper.selectOne(Wrappers.lambdaQuery(EnterpriseLevel.class)
			.eq(EnterpriseLevel::getTenantId, tenantId)
			.eq(EnterpriseLevel::getId, id));
		if (level == null) {
			throw new ArynBusinessException("企业等级不存在");
		}
		return level;
	}

	// ------------------------------------------------------------------
	// 权益配置
	// ------------------------------------------------------------------

	@Override
	public List<EnterpriseBenefit> listBenefits(String tenantId, String status, String keyword) {
		return enterpriseBenefitMapper.selectList(Wrappers.lambdaQuery(EnterpriseBenefit.class)
			.eq(EnterpriseBenefit::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), EnterpriseBenefit::getStatus, status)
			.like(StringUtils.hasText(keyword), EnterpriseBenefit::getBenefitName, keyword)
			.orderByAsc(EnterpriseBenefit::getBenefitType));
	}

	@Override
	public List<EnterpriseBenefit> listBenefitsByLevel(String tenantId, String levelId) {
		List<EnterpriseBenefitLevelRel> rels = enterpriseBenefitLevelRelMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseBenefitLevelRel.class)
					.eq(EnterpriseBenefitLevelRel::getTenantId, tenantId)
					.eq(EnterpriseBenefitLevelRel::getLevelId, levelId));
		if (rels.isEmpty()) {
			return List.of();
		}
		return enterpriseBenefitMapper.selectList(Wrappers.lambdaQuery(EnterpriseBenefit.class)
			.eq(EnterpriseBenefit::getTenantId, tenantId)
			.eq(EnterpriseBenefit::getStatus, "0")
			.in(EnterpriseBenefit::getId, rels.stream().map(EnterpriseBenefitLevelRel::getBenefitId).toList()));
	}

	@Override
	public List<String> listLevelIdsOfBenefit(String tenantId, String benefitId) {
		return enterpriseBenefitLevelRelMapper.selectList(Wrappers.lambdaQuery(EnterpriseBenefitLevelRel.class)
				.eq(EnterpriseBenefitLevelRel::getTenantId, tenantId)
				.eq(EnterpriseBenefitLevelRel::getBenefitId, benefitId))
			.stream()
			.map(EnterpriseBenefitLevelRel::getLevelId)
			.toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public EnterpriseBenefit saveBenefit(String tenantId, EnterpriseBenefit benefit, List<String> levelIds) {
		validateBenefit(benefit);
		benefit.setId(null);
		benefit.setTenantId(tenantId);
		enterpriseBenefitMapper.insert(benefit);
		replaceLevelRels(tenantId, benefit.getId(), levelIds);
		return benefit;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public EnterpriseBenefit updateBenefit(String tenantId, String id, EnterpriseBenefit benefit,
			List<String> levelIds) {
		EnterpriseBenefit existing = enterpriseBenefitMapper.selectOne(Wrappers.lambdaQuery(EnterpriseBenefit.class)
			.eq(EnterpriseBenefit::getTenantId, tenantId)
			.eq(EnterpriseBenefit::getId, id));
		if (existing == null) {
			throw new ArynBusinessException("企业权益不存在");
		}
		validateBenefit(benefit);
		benefit.setId(id);
		benefit.setTenantId(tenantId);
		enterpriseBenefitMapper.updateById(benefit);
		replaceLevelRels(tenantId, id, levelIds);
		return benefit;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteBenefit(String tenantId, String id) {
		EnterpriseBenefit existing = enterpriseBenefitMapper.selectOne(Wrappers.lambdaQuery(EnterpriseBenefit.class)
			.eq(EnterpriseBenefit::getTenantId, tenantId)
			.eq(EnterpriseBenefit::getId, id));
		if (existing == null) {
			return;
		}
		enterpriseBenefitMapper.deleteById(id);
		List<EnterpriseBenefitLevelRel> rels = enterpriseBenefitLevelRelMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseBenefitLevelRel.class)
					.eq(EnterpriseBenefitLevelRel::getTenantId, tenantId)
					.eq(EnterpriseBenefitLevelRel::getBenefitId, id));
		for (EnterpriseBenefitLevelRel rel : rels) {
			enterpriseBenefitLevelRelMapper.deleteById(rel.getId());
		}
	}

	private void validateBenefit(EnterpriseBenefit benefit) {
		if (!StringUtils.hasText(benefit.getBenefitName())) {
			throw new ArynBusinessException("权益名称不能为空");
		}
		switch (benefit.getBenefitType() == null ? "" : benefit.getBenefitType()) {
			case EnterpriseMemberConstants.BENEFIT_DISCOUNT -> {
				BigDecimal rate = parseDecimal(benefit.getBenefitValue());
				if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
					throw new ArynBusinessException("企业折扣率必须在 (0,1] 区间");
				}
			}
			case EnterpriseMemberConstants.BENEFIT_REBATE_RATE -> {
				BigDecimal rate = parseDecimal(benefit.getBenefitValue());
				if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
					throw new ArynBusinessException("返利率必须在 (0,100] 区间");
				}
			}
			case EnterpriseMemberConstants.BENEFIT_CREDIT_DAYS -> {
				BigDecimal days = parseDecimal(benefit.getBenefitValue());
				if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
					throw new ArynBusinessException("账期天数必须为正整数");
				}
			}
			case EnterpriseMemberConstants.BENEFIT_FREE_SHIPPING,
				 EnterpriseMemberConstants.BENEFIT_POLICY_POOL,
				 EnterpriseMemberConstants.BENEFIT_INVOICING,
				 EnterpriseMemberConstants.BENEFIT_DEDICATED_SERVICE -> {
				if (!StringUtils.hasText(benefit.getBenefitValue())) {
					throw new ArynBusinessException("权益值不能为空");
				}
			}
			default -> throw new ArynBusinessException("非法的权益类型");
		}
	}

	private void replaceLevelRels(String tenantId, String benefitId, List<String> levelIds) {
		List<EnterpriseBenefitLevelRel> rels = enterpriseBenefitLevelRelMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseBenefitLevelRel.class)
					.eq(EnterpriseBenefitLevelRel::getTenantId, tenantId)
					.eq(EnterpriseBenefitLevelRel::getBenefitId, benefitId));
		List<String> target = levelIds == null ? List.of() : levelIds.stream().filter(StringUtils::hasText).toList();
		for (EnterpriseBenefitLevelRel rel : rels) {
			if (!target.contains(rel.getLevelId())) {
				enterpriseBenefitLevelRelMapper.deleteById(rel.getId());
			}
		}
		for (String levelId : target) {
			boolean exists = rels.stream().anyMatch(rel -> rel.getLevelId().equals(levelId));
			if (!exists) {
				EnterpriseBenefitLevelRel rel = new EnterpriseBenefitLevelRel();
				rel.setBenefitId(benefitId);
				rel.setLevelId(levelId);
				rel.setTenantId(tenantId);
				enterpriseBenefitLevelRelMapper.insert(rel);
			}
		}
	}

	// ------------------------------------------------------------------
	// 成长值与等级重算
	// ------------------------------------------------------------------

	@Override
	public void accumulateOrderGrowth(OrderCompleteEvent event) {
		try {
			String subjectType = resolveSubjectType(event.getPurchaseScene());
			String subjectId = EnterpriseMemberConstants.SUBJECT_VESSEL.equals(subjectType)
					? event.getVesselId() : event.getBuyerOrgId();
			if (subjectType == null || !StringUtils.hasText(subjectId)
					|| event.getGoodsPaymentAmount() == null
					|| event.getGoodsPaymentAmount().compareTo(BigDecimal.ZERO) < 0) {
				return;
			}
			String tenantId = event.getTenantId();
			String period = YearMonth.now().format(PERIOD_FORMAT);
			enterpriseGrowthMapper.accumulate(IdUtil.getSnowflakeNextIdStr(), subjectType, subjectId, period,
					event.getGoodsPaymentAmount(), tenantId);
			recalculateSubjectLevel(tenantId, subjectType, subjectId, "订单完成累计：" + event.getOrderNo());
		}
		catch (Exception ex) {
			// 企业轨累计 fail-open：不影响个人积分/等级主流程，月度对账 Job 兜底收敛
			log.warn("企业采购额累计失败 orderId={}: {}", event == null ? null : event.getOrderId(), ex.getMessage());
		}
	}

	/** 购买场景 → 企业主体类型：2船供→船舶、3企业→岸上企业；个人（1/空）返回 null。 */
	private String resolveSubjectType(String purchaseScene) {
		if ("2".equals(purchaseScene)) {
			return EnterpriseMemberConstants.SUBJECT_VESSEL;
		}
		if ("3".equals(purchaseScene)) {
			return EnterpriseMemberConstants.SUBJECT_BUYER_ORG;
		}
		return null;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recalculateSubjectLevel(String tenantId, String subjectType, String subjectId, String reason) {
		BigDecimal rollingAmount = rollingConsumeAmount(tenantId, subjectType, subjectId);
		EnterpriseLevel target = bestLevel(tenantId, rollingAmount);
		String targetLevelId = target == null ? null : target.getId();
		EnterpriseLevelBinding binding = enterpriseLevelBindingMapper.selectOne(
				Wrappers.lambdaQuery(EnterpriseLevelBinding.class)
					.eq(EnterpriseLevelBinding::getTenantId, tenantId)
					.eq(EnterpriseLevelBinding::getSubjectType, subjectType)
					.eq(EnterpriseLevelBinding::getSubjectId, subjectId));
		String currentLevelId = binding == null ? null : binding.getLevelId();
		if (Objects.equals(currentLevelId, targetLevelId)) {
			return;
		}
		enterpriseLevelBindingMapper.upsert(IdUtil.getSnowflakeNextIdStr(), subjectType, subjectId,
				targetLevelId, tenantId);
		EnterpriseLevelRecord record = new EnterpriseLevelRecord();
		record.setSubjectType(subjectType);
		record.setSubjectId(subjectId);
		record.setOldLevelId(currentLevelId);
		record.setNewLevelId(targetLevelId);
		record.setChangeReason(reason);
		record.setTenantId(tenantId);
		enterpriseLevelRecordMapper.insert(record);
		log.info("企业主体等级变更 subject={}:{} {} → {} 近12月采购额={} reason={}",
				subjectType, subjectId, currentLevelId, targetLevelId, rollingAmount, reason);
	}

	/** 近 12 个自然月（含当月）采购额求和。 */
	private BigDecimal rollingConsumeAmount(String tenantId, String subjectType, String subjectId) {
		YearMonth now = YearMonth.now();
		List<String> periods = new ArrayList<>(ROLLING_MONTHS);
		for (int i = 0; i < ROLLING_MONTHS; i++) {
			periods.add(now.minusMonths(i).format(PERIOD_FORMAT));
		}
		return enterpriseGrowthMapper.selectList(Wrappers.lambdaQuery(EnterpriseGrowth.class)
				.eq(EnterpriseGrowth::getTenantId, tenantId)
				.eq(EnterpriseGrowth::getSubjectType, subjectType)
				.eq(EnterpriseGrowth::getSubjectId, subjectId)
				.in(EnterpriseGrowth::getPeriod, periods))
			.stream()
			.map(EnterpriseGrowth::getConsumeAmount)
			.filter(Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** 取满足门槛的最高档（sort_order 最大且 condition_value ≤ 采购额；单条脏配置跳过不阻断）。 */
	private EnterpriseLevel bestLevel(String tenantId, BigDecimal rollingAmount) {
		EnterpriseLevel best = null;
		for (EnterpriseLevel level : enterpriseLevelMapper.selectList(Wrappers.lambdaQuery(EnterpriseLevel.class)
				.eq(EnterpriseLevel::getTenantId, tenantId)
				.eq(EnterpriseLevel::getStatus, "0")
				.orderByAsc(EnterpriseLevel::getSortOrder))) {
			if (level.getConditionValue() == null) {
				continue;
			}
			if (rollingAmount.compareTo(level.getConditionValue()) >= 0
					&& (best == null || level.getSortOrder() > best.getSortOrder())) {
				best = level;
			}
		}
		return best;
	}

	@Override
	public int reconcileAllLevels(String tenantId, String reason) {
		List<EnterpriseLevelBinding> bindings = enterpriseLevelBindingMapper.selectList(
				Wrappers.lambdaQuery(EnterpriseLevelBinding.class)
					.eq(EnterpriseLevelBinding::getTenantId, tenantId));
		int changed = 0;
		for (EnterpriseLevelBinding binding : bindings) {
			try {
				recalculateSubjectLevel(tenantId, binding.getSubjectType(), binding.getSubjectId(), reason);
				changed++;
			}
			catch (Exception ex) {
				log.warn("企业等级对账失败 subject={}:{}: {}", binding.getSubjectType(), binding.getSubjectId(),
						ex.getMessage());
			}
		}
		return changed;
	}

	// ------------------------------------------------------------------
	// 权益聚合
	// ------------------------------------------------------------------

	@Override
	public EnterpriseBenefitsVO getSubjectBenefits(String tenantId, String subjectType, String subjectId) {
		if (!StringUtils.hasText(subjectType) || !StringUtils.hasText(subjectId)) {
			return null;
		}
		EnterpriseLevelBinding binding = enterpriseLevelBindingMapper.selectOne(
				Wrappers.lambdaQuery(EnterpriseLevelBinding.class)
					.eq(EnterpriseLevelBinding::getTenantId, tenantId)
					.eq(EnterpriseLevelBinding::getSubjectType, subjectType)
					.eq(EnterpriseLevelBinding::getSubjectId, subjectId));
		if (binding == null || !StringUtils.hasText(binding.getLevelId())) {
			return null;
		}
		EnterpriseLevel level = enterpriseLevelMapper.selectOne(Wrappers.lambdaQuery(EnterpriseLevel.class)
			.eq(EnterpriseLevel::getTenantId, tenantId)
			.eq(EnterpriseLevel::getId, binding.getLevelId())
			.eq(EnterpriseLevel::getStatus, "0"));
		if (level == null) {
			return null;
		}
		List<EnterpriseBenefit> benefits = listBenefitsByLevel(tenantId, level.getId());
		EnterpriseBenefitsVO vo = new EnterpriseBenefitsVO();
		vo.setSubjectType(subjectType);
		vo.setSubjectId(subjectId);
		vo.setLevelId(level.getId());
		vo.setLevelName(level.getLevelName());
		vo.setLevelSortOrder(level.getSortOrder());
		List<String> policyPoolIds = new ArrayList<>();
		for (EnterpriseBenefit benefit : benefits) {
			switch (benefit.getBenefitType()) {
				case EnterpriseMemberConstants.BENEFIT_DISCOUNT -> {
					BigDecimal rate = parseDecimal(benefit.getBenefitValue());
					// 多档取最优（最小折扣率）
					if (rate != null && (vo.getDiscountRate() == null || rate.compareTo(vo.getDiscountRate()) < 0)) {
						vo.setDiscountRate(rate);
					}
				}
				case EnterpriseMemberConstants.BENEFIT_FREE_SHIPPING -> vo.setFreeShipping(Boolean.TRUE);
				case EnterpriseMemberConstants.BENEFIT_POLICY_POOL -> {
					if (StringUtils.hasText(benefit.getBenefitValue())) {
						policyPoolIds.addAll(List.of(benefit.getBenefitValue().split(",")));
					}
				}
				case EnterpriseMemberConstants.BENEFIT_INVOICING -> vo.setInvoicingEnabled(Boolean.TRUE);
				case EnterpriseMemberConstants.BENEFIT_DEDICATED_SERVICE -> vo.setDedicatedService(Boolean.TRUE);
				case EnterpriseMemberConstants.BENEFIT_REBATE_RATE -> {
					BigDecimal rate = parseDecimal(benefit.getBenefitValue());
					// 预留字段：多档取最大
					if (rate != null && (vo.getRebateRate() == null || rate.compareTo(vo.getRebateRate()) > 0)) {
						vo.setRebateRate(rate);
					}
				}
				case EnterpriseMemberConstants.BENEFIT_CREDIT_DAYS -> {
					BigDecimal days = parseDecimal(benefit.getBenefitValue());
					if (days != null && (vo.getCreditDays() == null || days.intValue() > vo.getCreditDays())) {
						vo.setCreditDays(days.intValue());
					}
				}
				default -> log.warn("企业权益[{}]类型非法，跳过: {}", benefit.getId(), benefit.getBenefitType());
			}
		}
		vo.setPolicyPoolIds(policyPoolIds.isEmpty() ? List.of() : policyPoolIds.stream().distinct().toList());
		return vo;
	}

	private BigDecimal parseDecimal(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
