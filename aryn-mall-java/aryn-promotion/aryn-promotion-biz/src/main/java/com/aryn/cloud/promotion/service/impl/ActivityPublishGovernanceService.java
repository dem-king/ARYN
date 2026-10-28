package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 活动发布治理：规则结构校验与发布冲突检索。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityPublishGovernanceService {

	private final PromotionActivityMapper promotionActivityMapper;

	/**
	 * 会员价（type=10）发布时的等级门槛存在性校验：定义在 user-api、实现挂 user-biz。
	 *
	 * <p>必须用<b>非 final 字段</b>+@DubboReference：构造器注入远程接口曾在 boot 启动失败
	 * （与 NewbieActivityServiceImpl 同款踩坑）；查询异常按校验失败处理（fail-closed），
	 * 不允许把无效等级门槛的活动发布出去。
	 */
	@DubboReference
	private RemoteMemberLevelService remoteMemberLevelService;

	/**
	 * 发布前规则结构校验：
	 * 阶梯价 skuLadders[{skuId,ladders[{minQty,unitPrice}]}]（须指定SKU/常购用户范围且与 scopeValue 一一对应；
	 * 每 SKU 内 minQty 不得重复，且数量越大单价必须越低）；
	 * 整船优惠 tiers[{minAmount,discountAmount}]（minAmount 不得重复，金额越大优惠不得减少）；
	 * 买赠 gifts[{skuId,quantity,minAmount}]；常购专属价 skuPrices[{skuId,unitPrice}]；
	 * 会员价 minLevelId + skus[{skuId,unitPrice}]（指定 SKU 范围，等级门槛须存在）。
	 */
	public void validateRules(PromotionActivity activity) {
		if (!StringUtils.hasText(activity.getRules())) {
			throw new ArynBusinessException("活动规则不能为空");
		}
		try {
			JSONObject rules = JSONUtil.parseObj(activity.getRules());
			switch (activity.getActivityType() == null ? "" : activity.getActivityType()) {
				case PromotionActivity.TYPE_LADDER_PRICE -> validateSkuLadders(activity, rules);
				case PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT -> validateTiers(rules);
				case PromotionActivity.TYPE_GIFT -> validateGifts(rules);
				case PromotionActivity.TYPE_NEWBIE_PRICE -> validateNewbiePrice(activity, rules);
				case PromotionActivity.TYPE_MEMBER_PRICE -> validateMemberPrice(activity, rules);
				default -> throw new ArynBusinessException("暂不支持的活动类型：" + activity.getActivityType());
			}
		}
		catch (ArynBusinessException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ArynBusinessException("活动规则 JSON 不合法");
		}
	}

	/**
	 * 阶梯价必须使用「指定SKU」或「常购用户」范围（全场单档价会串到所有商品），
	 * rules.skuLadders 与 scopeValue 按 SKU 一一对应。
	 */
	private void validateSkuLadders(PromotionActivity activity, JSONObject rules) {
		if (!"2".equals(activity.getScopeType()) && !"8".equals(activity.getScopeType())) {
			throw new ArynBusinessException("阶梯价活动必须使用「指定SKU」或「常购用户」范围");
		}
		JSONArray skuLadders = rules.getJSONArray("skuLadders");
		if (skuLadders == null || skuLadders.isEmpty()) {
			throw new ArynBusinessException("阶梯价活动必须配置 skuLadders 逐 SKU 档位");
		}
		Set<String> skuIds = new HashSet<>();
		for (Object element : skuLadders) {
			JSONObject skuLadder = (JSONObject) element;
			String skuId = skuLadder.getStr("skuId");
			if (!StringUtils.hasText(skuId)) {
				throw new ArynBusinessException("阶梯价 skuLadders 必须包含 skuId");
			}
			if (!skuIds.add(skuId)) {
				throw new ArynBusinessException("阶梯价 SKU 重复：" + skuId);
			}
			JSONArray ladders = skuLadder.getJSONArray("ladders");
			if (ladders == null || ladders.isEmpty()) {
				throw new ArynBusinessException("阶梯价 SKU " + skuId + " 必须配置档位");
			}
			validateLadderEntries(ladders);
		}
		if (!StringUtils.hasText(activity.getScopeValue())) {
			throw new ArynBusinessException("阶梯价活动必须配置指定商品范围");
		}
		Set<String> scopeSkuIds = new HashSet<>(List.of(activity.getScopeValue().split(",")));
		if (!scopeSkuIds.equals(skuIds)) {
			throw new ArynBusinessException("阶梯价 skuLadders 与指定商品范围必须一一对应");
		}
	}

	private void validateLadderEntries(JSONArray ladders) {
		List<JSONObject> sorted = ladders.stream()
				.map(element -> (JSONObject) element)
				.sorted(Comparator.comparingInt(ladder -> nullSafe(ladder.getInt("minQty"))))
				.toList();
		Integer lastMinQty = null;
		BigDecimal lastUnitPrice = null;
		for (JSONObject ladder : sorted) {
			Integer minQty = ladder.getInt("minQty");
			BigDecimal unitPrice = ladder.getBigDecimal("unitPrice");
			if (minQty == null || minQty < 1 || unitPrice == null || unitPrice.signum() <= 0) {
				throw new ArynBusinessException("阶梯价档位必须包含正数 unitPrice 与 minQty");
			}
			if (minQty.equals(lastMinQty)) {
				throw new ArynBusinessException("阶梯价档位 minQty 重复：" + minQty);
			}
			if (lastUnitPrice != null && unitPrice.compareTo(lastUnitPrice) >= 0) {
				throw new ArynBusinessException("阶梯价单价必须随数量递增而递减");
			}
			lastMinQty = minQty;
			lastUnitPrice = unitPrice;
		}
	}

	private void validateTiers(JSONObject rules) {
		JSONArray tiers = rules.getJSONArray("tiers");
		if (tiers == null || tiers.isEmpty()) {
			throw new ArynBusinessException("整船优惠活动必须配置 tiers 档位");
		}
		List<JSONObject> sorted = tiers.stream()
				.map(element -> (JSONObject) element)
				.sorted(Comparator.comparing(tier -> nullSafeDecimal(tier.getBigDecimal("minAmount"))))
				.toList();
		BigDecimal lastMinAmount = null;
		BigDecimal lastDiscount = null;
		for (JSONObject tier : sorted) {
			BigDecimal minAmount = tier.getBigDecimal("minAmount");
			BigDecimal discountAmount = tier.getBigDecimal("discountAmount");
			if (minAmount == null || minAmount.signum() < 0 || discountAmount == null
					|| discountAmount.signum() <= 0) {
				throw new ArynBusinessException("整船优惠档位必须包含正数 discountAmount 与 minAmount");
			}
			if (lastMinAmount != null && minAmount.compareTo(lastMinAmount) == 0) {
				throw new ArynBusinessException("整船优惠档位 minAmount 重复：" + minAmount);
			}
			if (lastDiscount != null && discountAmount.compareTo(lastDiscount) < 0) {
				throw new ArynBusinessException("整船优惠金额必须随门槛递增而不减少");
			}
			lastMinAmount = minAmount;
			lastDiscount = discountAmount;
		}
	}

	private void validateGifts(JSONObject rules) {
		JSONArray gifts = rules.getJSONArray("gifts");
		if (gifts == null || gifts.isEmpty()) {
			throw new ArynBusinessException("买赠活动必须配置 gifts 赠品");
		}
		BigDecimal minAmount = rules.getBigDecimal("minAmount");
		if (minAmount == null || minAmount.signum() < 0) {
			throw new ArynBusinessException("买赠活动必须配置非负的 minAmount 门槛");
		}
		Set<String> skuIds = new HashSet<>();
		for (Object element : gifts) {
			JSONObject gift = (JSONObject) element;
			String skuId = gift.getStr("skuId");
			Integer quantity = gift.getInt("quantity");
			if (!StringUtils.hasText(skuId) || quantity == null || quantity < 1) {
				throw new ArynBusinessException("买赠赠品位必须包含 skuId 与正数 quantity");
			}
			if (!skuIds.add(skuId)) {
				throw new ArynBusinessException("买赠赠品 SKU 重复：" + skuId);
			}
		}
	}

	/**
	 * 新人专享价：registerWithinDays 为正整数（可空=不限时效）、limitPerUser 必须为 1、
	 * rules.skus 与 scopeValue（指定 SKU 范围）必须一一对应且不重复、每个 price 为正数。
	 * 价格与 SKU 售价的相对关系在引擎侧跳过（命中价不低于基价不产生覆盖），发布时不查商品库。
	 */
	private void validateNewbiePrice(PromotionActivity activity, JSONObject rules) {
		if (!"2".equals(activity.getScopeType())) {
			throw new ArynBusinessException("新人专享价必须使用「指定商品」范围");
		}
		Integer registerWithinDays = rules.getInt("registerWithinDays");
		if (registerWithinDays != null && registerWithinDays < 1) {
			throw new ArynBusinessException("新人专享价 registerWithinDays 必须为正整数");
		}
		Integer limitPerUser = rules.getInt("limitPerUser");
		if (limitPerUser == null || limitPerUser != 1) {
			throw new ArynBusinessException("新人专享价每人限购固定为 1 件");
		}
		JSONArray skus = rules.getJSONArray("skus");
		if (skus == null || skus.isEmpty()) {
			throw new ArynBusinessException("新人专享价必须配置 skus 单价列表");
		}
		Set<String> ruleSkuIds = new HashSet<>();
		for (Object element : skus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal("price");
			if (!StringUtils.hasText(skuId) || price == null || price.signum() <= 0) {
				throw new ArynBusinessException("新人专享价 skus 必须包含 skuId 与正数 price");
			}
			if (!ruleSkuIds.add(skuId)) {
				throw new ArynBusinessException("新人专享价 SKU 重复：" + skuId);
			}
		}
		if (!StringUtils.hasText(activity.getScopeValue())) {
			throw new ArynBusinessException("新人专享价必须配置指定商品范围");
		}
		Set<String> scopeSkuIds = new HashSet<>(List.of(activity.getScopeValue().split(",")));
		if (!scopeSkuIds.equals(ruleSkuIds)) {
			throw new ArynBusinessException("新人专享价 skus 与指定商品范围必须一一对应");
		}
	}

	/**
	 * 会员价：必须使用「指定商品」范围；minLevelId 必填且经 user 域远程接口校验存在
	 * （查询异常按校验失败处理，fail-closed 不允许发布无效等级门槛）；
	 * rules.skus 与 scopeValue（指定 SKU 范围）必须一一对应且不重复，每个 unitPrice 为正数。
	 * 价格与 SKU 售价的相对关系在引擎侧跳过（会员价不低于基价不产生覆盖），发布时不查商品库。
	 *
	 * <p>等级是否存在之外不按 member_level.status 过滤：与用户域 getCurrentMemberInfo 口径一致
	 * （禁用等级的历史用户仍可能持有该等级ID，发布侧不做额外收窄）。
	 */
	private void validateMemberPrice(PromotionActivity activity, JSONObject rules) {
		if (!"2".equals(activity.getScopeType())) {
			throw new ArynBusinessException("会员价活动必须使用「指定商品」范围");
		}
		String minLevelId = rules.getStr("minLevelId");
		if (!StringUtils.hasText(minLevelId)) {
			throw new ArynBusinessException("会员价活动必须配置 minLevelId 会员等级门槛");
		}
		assertMemberLevelExists(minLevelId);
		JSONArray skus = rules.getJSONArray("skus");
		if (skus == null || skus.isEmpty()) {
			throw new ArynBusinessException("会员价活动必须配置 skus 单价列表");
		}
		Set<String> ruleSkuIds = new HashSet<>();
		for (Object element : skus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal unitPrice = sku.getBigDecimal("unitPrice");
			if (!StringUtils.hasText(skuId) || unitPrice == null || unitPrice.signum() <= 0) {
				throw new ArynBusinessException("会员价 skus 必须包含 skuId 与正数 unitPrice");
			}
			if (!ruleSkuIds.add(skuId)) {
				throw new ArynBusinessException("会员价 SKU 重复：" + skuId);
			}
		}
		if (!StringUtils.hasText(activity.getScopeValue())) {
			throw new ArynBusinessException("会员价活动必须配置指定商品范围");
		}
		Set<String> scopeSkuIds = new HashSet<>(List.of(activity.getScopeValue().split(",")));
		if (!scopeSkuIds.equals(ruleSkuIds)) {
			throw new ArynBusinessException("会员价 skus 与指定商品范围必须一一对应");
		}
	}

	/**
	 * 等级门槛存在性校验：远程返回 null 视为等级不存在；远程异常视为校验失败（fail-closed），
	 * 不允许在等级校验不可用时把活动发布出去（否则引擎侧所有用户都无资格，活动等于空转）。
	 */
	private void assertMemberLevelExists(String minLevelId) {
		MemberLevelSnapshotVO level;
		try {
			level = remoteMemberLevelService.getLevelById(ArynTenantContextHolder.getTenantId(), minLevelId);
		}
		catch (Exception ex) {
			log.warn("会员价发布校验查询会员等级失败 levelId={}: {}", minLevelId, ex.getMessage());
			throw new ArynBusinessException("会员等级校验失败，请稍后重试");
		}
		if (level == null) {
			throw new ArynBusinessException("会员等级不存在：" + minLevelId);
		}
	}

	private int nullSafe(Integer value) {
		return value != null ? value : Integer.MAX_VALUE;
	}

	private BigDecimal nullSafeDecimal(BigDecimal value) {
		return value != null ? value : java.math.BigDecimal.valueOf(Long.MAX_VALUE);
	}

	/**
	 * 发布冲突：同租户同类型、状态为已发布/已暂停、时间区间与本活动重叠的其他活动。
	 */
	public List<PromotionActivity> listPublishConflicts(String tenantId, PromotionActivity activity) {
		return promotionActivityMapper.selectList(Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, tenantId)
				.eq(PromotionActivity::getActivityType, activity.getActivityType())
				.in(PromotionActivity::getStatus, List.of(PromotionActivity.STATUS_PUBLISHED,
						PromotionActivity.STATUS_PAUSED))
				.ne(activity.getId() != null, PromotionActivity::getId, activity.getId())
				.lt(PromotionActivity::getStartTime, activity.getEndTime())
				.gt(PromotionActivity::getEndTime, activity.getStartTime()));
	}

}
