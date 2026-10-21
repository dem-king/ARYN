package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService;
import com.aryn.cloud.promotion.api.vo.AppNewbieFloorVO;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.INewbieActivityService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 新人专享楼层实现：读进行中的新人价活动并组装商品列表。
 *
 * <p>两个 C 端入口共用同一份「活动选择」口径（优先级最高且规则可用的活动）：
 * 楼层接口返回全部配置商品、不判资格（营销面，免登）；指定 SKU 接口只返回请求命中的
 * 商品，并在携带登录态时附带当前用户的资格判定（商详 / 购物车展示用）。
 *
 * <p>商品信息（名称/图/当前售价）经 RemoteGoodsSkuService 批量查询后回填；
 * 商品下架或查询失败时对应项降级剔除，不阻断楼层整体渲染。
 *
 * @author aryn
 * @since 2026/10/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewbieActivityServiceImpl implements INewbieActivityService {

	/** 楼层最多展示的商品数，防单活动配过多 SKU 拖慢接口 */
	private static final int MAX_FLOOR_GOODS = 20;

	/**
	 * 指定 SKU 查询的单次请求上限。
	 *
	 * <p>购物车整车结算一次最多带 100 行，这里留出余量；上限只用于防止请求体无限放大
	 * （超出部分忽略，不报错——展示缺失只影响价格标注，不影响下单）。
	 */
	private static final int MAX_PRICE_QUERY_SKUS = 200;

	private final PromotionActivityMapper promotionActivityMapper;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	/**
	 * 资格判定走回调式远程接口（接口定义在 promotion-api、实现挂 order-biz）：
	 * 订单数据的归属方是 order-biz，promotion 侧不能直连订单表。
	 * 与 PromotionEngineServiceImpl 同款非 final 字段注入（构造器注入远程接口曾在 boot 启动失败）。
	 */
	@DubboReference
	private RemoteBuyerProfileService remoteBuyerProfileService;

	@Override
	public AppNewbieFloorVO getCurrentFloor() {
		for (PromotionActivity activity : activeActivities()) {
			AppNewbieFloorVO floor = toFloor(activity);
			if (floor != null) {
				return floor;
			}
		}
		return null;
	}

	@Override
	public AppNewbiePriceVO getSkuPrices(List<String> skuIds, String userId) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return null;
		}
		// 去重但保留请求顺序：同一 SPU 下前端可能把多个规格 SKU 一起带上来
		Set<String> wanted = new LinkedHashSet<>();
		for (String skuId : skuIds) {
			if (StringUtils.hasText(skuId) && wanted.size() < MAX_PRICE_QUERY_SKUS) {
				wanted.add(skuId);
			}
		}
		if (wanted.isEmpty()) {
			return null;
		}
		for (PromotionActivity activity : activeActivities()) {
			AppNewbiePriceVO price = toSkuPrices(activity, wanted, userId);
			if (price != null) {
				return price;
			}
		}
		return null;
	}

	/**
	 * 进行中的新人价活动，按优先级降序（与楼层「多活动并存取优先级最高」的展示语义一致）。
	 */
	private List<PromotionActivity> activeActivities() {
		LocalDateTime now = LocalDateTime.now();
		return promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
					.eq(PromotionActivity::getTenantId, ArynTenantContextHolder.getTenantId())
					.eq(PromotionActivity::getActivityType, PromotionActivity.TYPE_NEWBIE_PRICE)
					.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.le(PromotionActivity::getStartTime, now)
					.ge(PromotionActivity::getEndTime, now)
					.orderByDesc(PromotionActivity::getPriority)
					.orderByDesc(PromotionActivity::getCreateTime));
	}

	private AppNewbieFloorVO toFloor(PromotionActivity activity) {
		// 楼层展示有上限（防单活动配过多 SKU 拖慢接口）：与展示上限一致地截断
		Map<String, BigDecimal> priceBySku = parseSkuPrices(activity, MAX_FLOOR_GOODS);
		if (priceBySku.isEmpty()) {
			return null;
		}
		AppNewbieFloorVO floor = new AppNewbieFloorVO();
		applyActivityBase(activity, floor::setActivityId, floor::setActivityName, floor::setEndTime,
				floor::setRegisterWithinDays, floor::setLimitPerUser);
		Map<String, GoodsSku> skuMap = loadSkuMap(priceBySku.keySet());
		List<AppNewbieFloorVO.NewbieGoodsVO> goodsList = new ArrayList<>();
		for (Map.Entry<String, BigDecimal> entry : priceBySku.entrySet()) {
			GoodsSku sku = skuMap.get(entry.getKey());
			if (sku == null) {
				// 商品已删除/下架：剔除该卡片，不渲染空价商品
				continue;
			}
			AppNewbieFloorVO.NewbieGoodsVO goods = new AppNewbieFloorVO.NewbieGoodsVO();
			goods.setSkuId(sku.getId());
			goods.setSpuId(sku.getSpuId());
			goods.setGoodsName(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getName() : null);
			goods.setGoodsImage(sku.getPicUrl());
			goods.setNewbiePrice(entry.getValue());
			goods.setOriginalPrice(sku.getSalesPrice());
			goodsList.add(goods);
		}
		if (goodsList.isEmpty()) {
			return null;
		}
		floor.setGoodsList(goodsList);
		return floor;
	}

	/**
	 * 指定 SKU 的新人价：只保留「请求 SKU ∩ 活动配置 SKU」中被商品库确认存在的部分。
	 *
	 * <p>与楼层一致的价格口径：下发活动配置价与当前售价两个值，是否真正更便宜
	 * 由展示端判定（与下单链「活动价必须低于当前基价才改价」的判定位置解耦，
	 * 避免重放场景两次判定不一致）。
	 *
	 * <p>此处**不套用楼层的 20 条展示上限**：请求集合由页面实际展示的商品决定，本身有界，
	 * 而结算链（引擎 preview）对活动配置的每个 SKU 都生效——显示端若按 20 截断，
	 * 第 21 个之后的 SKU 会出现「页面标原价、结算按新人价」的反向错位。
	 */
	private AppNewbiePriceVO toSkuPrices(PromotionActivity activity, Set<String> wanted, String userId) {
		Map<String, BigDecimal> priceBySku = parseSkuPrices(activity, Integer.MAX_VALUE);
		if (priceBySku.isEmpty()) {
			return null;
		}
		Map<String, GoodsSku> skuMap = loadSkuMap(wanted);
		List<AppNewbiePriceVO.NewbieSkuPriceVO> items = new ArrayList<>();
		for (String skuId : wanted) {
			BigDecimal newbiePrice = priceBySku.get(skuId);
			GoodsSku sku = skuMap.get(skuId);
			if (newbiePrice == null || sku == null) {
				continue;
			}
			AppNewbiePriceVO.NewbieSkuPriceVO item = new AppNewbiePriceVO.NewbieSkuPriceVO();
			item.setSkuId(sku.getId());
			item.setSpuId(sku.getSpuId());
			item.setNewbiePrice(newbiePrice);
			item.setOriginalPrice(sku.getSalesPrice());
			items.add(item);
		}
		if (items.isEmpty()) {
			return null;
		}
		AppNewbiePriceVO vo = new AppNewbiePriceVO();
		applyActivityBase(activity, vo::setActivityId, vo::setActivityName, vo::setEndTime,
				vo::setRegisterWithinDays, vo::setLimitPerUser);
		vo.setItems(items);
		// 未登录不下发资格字段（null）：展示端无从判定，按「可享新人价」处理，
		// 资格最终在下单链路硬校验。登录态下失败一律按不合格返回（fail-closed），
		// 与引擎判定同一份远程实现。
		if (StringUtils.hasText(userId)) {
			NewbieEligibilityVO eligibility = checkEligibility(userId, vo.getRegisterWithinDays());
			vo.setEligible(eligibility != null && Boolean.TRUE.equals(eligibility.getEligible()));
			vo.setIneligibleReason(eligibility != null ? eligibility.getReason() : "ERROR");
		}
		return vo;
	}

	/** 活动基础字段回填（楼层与指定 SKU 两个出口同源，避免两处各读一遍 rules）。 */
	private void applyActivityBase(PromotionActivity activity, java.util.function.Consumer<String> activityId,
			java.util.function.Consumer<String> activityName,
			java.util.function.Consumer<LocalDateTime> endTime,
			java.util.function.Consumer<Integer> registerWithinDays,
			java.util.function.Consumer<Integer> limitPerUser) {
		activityId.accept(activity.getId());
		activityName.accept(activity.getActivityName());
		endTime.accept(activity.getEndTime());
		JSONObject rules = JSONUtil.parseObj(activity.getRules());
		registerWithinDays.accept(rules.getInt("registerWithinDays"));
		Integer limit = rules.getInt("limitPerUser");
		limitPerUser.accept(limit != null ? limit : 1);
	}

	/**
	 * 资格判定：异常按不合格返回（fail-closed），不阻断展示。
	 *
	 * <p>展示端的资格只用于「按原价展示 + 说明原因」，真正拦截仍在下单链路，
	 * 因此这里不抛异常、失败即视为不可享。
	 */
	private NewbieEligibilityVO checkEligibility(String userId, Integer registerWithinDays) {
		try {
			return remoteBuyerProfileService.checkNewbieEligibility(
					ArynTenantContextHolder.getTenantId(), userId, registerWithinDays);
		}
		catch (Exception ex) {
			log.warn("新人价展示资格查询失败，按非新人处理 user={}: {}", userId, ex.getMessage());
			NewbieEligibilityVO fallback = new NewbieEligibilityVO();
			fallback.setEligible(Boolean.FALSE);
			fallback.setReason("ERROR");
			return fallback;
		}
	}

	/**
	 * rules.skus 的 SKU→新人价映射（保持配置顺序）。
	 *
	 * @param maxEntries 解析上限：楼层传展示上限（控制响应体积），
	 *                   指定 SKU 查询传无限（其请求集合自身有界，见 toSkuPrices）
	 */
	private Map<String, BigDecimal> parseSkuPrices(PromotionActivity activity, int maxEntries) {
		Map<String, BigDecimal> priceBySku = new LinkedHashMap<>();
		try {
			JSONArray skus = JSONUtil.parseObj(activity.getRules()).getJSONArray("skus");
			if (skus == null) {
				return priceBySku;
			}
			for (Object element : skus) {
				JSONObject sku = (JSONObject) element;
				String skuId = sku.getStr("skuId");
				BigDecimal price = sku.getBigDecimal("price");
				if (StringUtils.hasText(skuId) && price != null && price.signum() > 0
						&& priceBySku.size() < maxEntries) {
					priceBySku.put(skuId, price);
				}
			}
		}
		catch (Exception ex) {
			log.warn("新人专享活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
		}
		return priceBySku;
	}

	private Map<String, GoodsSku> loadSkuMap(java.util.Set<String> skuIds) {
		try {
			return remoteGoodsSkuService.getBySkuIds(new ArrayList<>(skuIds)).stream()
				.collect(java.util.stream.Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		}
		catch (Exception ex) {
			log.warn("新人专享楼层查询商品失败, skuCount={}", skuIds.size(), ex);
			return Map.of();
		}
	}

}
