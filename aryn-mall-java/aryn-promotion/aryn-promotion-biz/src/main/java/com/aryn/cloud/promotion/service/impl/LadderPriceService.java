package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.AppLadderPriceVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端阶梯价展示查询（2026-11-15 审查修复新增，方案 §8「商详阶梯价表格」）。
 *
 * <p>展示口径与成交同源的活动筛选（已发布 + 时间窗 + 档期窗 + 场景/船舶圈定），
 * 档位解析与引擎 {@code applyBestLadderPrice} 同形态（rules.skuLadders）；
 * 同 SKU 多活动命中取最高档单价最低者做确定性展示，成交仍以引擎试算为准。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LadderPriceService {

	private final PromotionActivityMapper promotionActivityMapper;

	private final PromoCalendarService promoCalendarService;

	/**
	 * 批量查询 SKU 阶梯档位表。
	 * @param skuIds 请求 SKU（空返回空列表）
	 * @param purchaseScene 当前购买场景（空=不过滤场景维度）
	 * @param vesselId 当前船舶上下文（空=船舶圈定活动不展示，fail-closed）
	 */
	public List<AppLadderPriceVO> ladderPrices(String tenantId, List<String> skuIds, String purchaseScene,
			String vesselId) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return List.of();
		}
		LocalDateTime now = LocalDateTime.now();
		List<PromotionActivity> activities = promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
					.eq(PromotionActivity::getTenantId, tenantId)
					.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.eq(PromotionActivity::getActivityType, PromotionActivity.TYPE_LADDER_PRICE)
					.le(PromotionActivity::getStartTime, now)
					.ge(PromotionActivity::getEndTime, now));
		if (activities.isEmpty()) {
			return List.of();
		}
		activities = promoCalendarService.filterByCalendarWindow(tenantId, activities, now);
		Map<String, AppLadderPriceVO> bestBySku = new LinkedHashMap<>();
		Map<String, BigDecimal> bestTopPriceBySku = new LinkedHashMap<>();
		for (PromotionActivity activity : activities) {
			if (!promoCalendarService.matchesDisplayScope(activity, purchaseScene, vesselId)) {
				continue;
			}
			try {
				JSONArray skuLadders = JSONUtil.parseObj(activity.getRules()).getJSONArray("skuLadders");
				if (skuLadders == null) {
					continue;
				}
				for (Object element : skuLadders) {
					JSONObject skuLadder = (JSONObject) element;
					String skuId = skuLadder.getStr("skuId");
					if (!StringUtils.hasText(skuId) || !skuIds.contains(skuId)) {
						continue;
					}
					List<AppLadderPriceVO.LadderTier> tiers = parseTiers(skuLadder.getJSONArray("ladders"));
					if (tiers.isEmpty()) {
						continue;
					}
					// 确定性展示：最高档单价更低者胜出（成交口径仍以引擎总价优惠最大为准）
					BigDecimal topPrice = tiers.get(tiers.size() - 1).getUnitPrice();
					BigDecimal currentBest = bestTopPriceBySku.get(skuId);
					if (currentBest != null && topPrice.compareTo(currentBest) >= 0) {
						continue;
					}
					AppLadderPriceVO vo = new AppLadderPriceVO();
					vo.setSkuId(skuId);
					vo.setActivityId(activity.getId());
					vo.setActivityName(activity.getActivityName());
					vo.setLadders(tiers);
					bestBySku.put(skuId, vo);
					bestTopPriceBySku.put(skuId, topPrice);
				}
			}
			catch (Exception ex) {
				log.warn("阶梯价活动[{}]规则解析失败，跳过", activity.getId(), ex);
			}
		}
		return new ArrayList<>(bestBySku.values());
	}

	/** 档位解析：minQty/unitPrice 合法且单价为正，按 minQty 升序（与引擎同形态）。 */
	private List<AppLadderPriceVO.LadderTier> parseTiers(JSONArray ladders) {
		List<AppLadderPriceVO.LadderTier> tiers = new ArrayList<>();
		if (ladders == null) {
			return tiers;
		}
		for (Object element : ladders) {
			JSONObject ladder = (JSONObject) element;
			Integer minQty = ladder.getInt("minQty");
			BigDecimal unitPrice = ladder.getBigDecimal("unitPrice");
			if (minQty == null || minQty < 1 || unitPrice == null || unitPrice.signum() <= 0) {
				continue;
			}
			AppLadderPriceVO.LadderTier tier = new AppLadderPriceVO.LadderTier();
			tier.setMinQty(minQty);
			tier.setUnitPrice(unitPrice);
			tiers.add(tier);
		}
		tiers.sort(Comparator.comparing(AppLadderPriceVO.LadderTier::getMinQty));
		return tiers;
	}

}