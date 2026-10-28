package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.AppMemberPriceVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.IMemberPriceActivityService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员价查询实现（C 端展示）：读进行中的会员价活动（promotion_activity type=10）并组装商品价格。
 *
 * <p>与新人价接口（{@link NewbieActivityServiceImpl}）同款口径：
 * <ol>
 *   <li><b>活动选择</b>：进行中活动按优先级降序取「首个能产出请求 SKU 交集」的活动
 *       （与新人价「多活动并存取优先级最高」一致；发布治理会提示同类型时间重叠）；</li>
 *   <li><b>商品回填</b>：仅回填商品库确认存在的 SKU（远程批量查询，失败降级剔除，不阻断）；</li>
 *   <li><b>资格 fail-closed</b>：登录用户经 Dubbo 查等级快照，等级缺失/远程异常一律按不合格，
 *       等级门槛快照同样查询失败即视为不可用（不下发该活动）；未登录不下发资格字段；</li>
 *   <li><b>价格口径与下单解耦</b>：只下发活动配置价与当前售价，是否真正更便宜（倒挂回退）
 *       由展示端按 {@code memberPrice < originalPrice} 判定，与引擎「不低于基价不改价」解耦。</li>
 * </ol>
 *
 * <p>等级查询结果按单次请求缓存，避免一个活动配置多个 SKU 时重复查等级；不做跨请求缓存
 * （级别可能随用户升级变化，跨请求缓存会导致会员权益延迟生效）。
 *
 * <p>两种查询入口，同一份活动解析与资格判定口径：
 * <ul>
 *   <li>{@link #getSkuPrices}：商详 / 购物车等已确定具体 SKU 的页面，按精确 SKU 出价；</li>
 *   <li>{@link #getSpuPrices}：商品列表卡片只知道 SPU（卡片价是 SPU 聚合价），按活动配置的
 *       SKU 反查 spuId 出价；同一 SPU 可能返回多条（不同规格），由 C 端按卡片展示价对应的
 *       SKU 精确匹配，后端不做挑选。</li>
 * </ul>
 *
 * <p>除活动价外，登录用户还会下发 {@code userDiscountRate}（member_benefit 折扣率权益）：
 * 会员价活动与会员折扣率是两套体系，结算侧按「会员价 vs 原价 × 会员折扣率」取优成交，
 * 展示侧必须用同一取优口径算展示价，否则「本区享 X 折」文案会与实际成交价不符。
 * 该值仅供展示取优，权威口径在订单结算侧（查询异常按 1=不打折 fail-open，不阻断渲染）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberPriceActivityServiceImpl implements IMemberPriceActivityService {

	/**
	 * 指定 SKU 查询的单次请求上限。
	 *
	 * <p>与新人价接口一致：上限只用于防止请求体无限放大（超出部分静默忽略，不报错——
	 * 展示缺失只影响价格标注，不影响下单）。
	 */
	private static final int MAX_PRICE_QUERY_SKUS = 200;

	/**
	 * 按 SPU 查询的单次请求上限。
	 *
	 * <p>列表一屏商品数远小于该值（分页 10~50），上限只防请求体放大；超出部分静默忽略。
	 */
	private static final int MAX_PRICE_QUERY_SPUS = 50;

	/**
	 * 按 SPU 查询时单活动回填商品库的配置 SKU 上限保护。
	 *
	 * <p>列表场景只能拿活动配置的 SKU 反查 spuId（会员价配在 SKU 上、C 端列表不知道 skuId），
	 * 活动配置理论上无硬上限，这里兜底 500 防把一次列表请求放大成慢查询；
	 * 超限静默截断，只影响价格标注、不影响下单（真享价以结算链引擎为准）。
	 */
	private static final int MAX_CONFIGURED_SKUS = 500;

	private final PromotionActivityMapper promotionActivityMapper;

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	/**
	 * 会员等级快照走 user 域 Dubbo 接口（定义在 user-api、实现挂 user-biz）。
	 *
	 * <p>必须用<b>非 final 字段</b>+@DubboReference：与 {@link NewbieActivityServiceImpl} 的
	 * remoteBuyerProfileService 同款写法——构造器注入远程接口曾在 boot 启动失败。
	 */
	@DubboReference
	private RemoteMemberLevelService remoteMemberLevelService;

	/**
	 * 会员权益（折扣率）查询走 user 域既有 Dubbo 接口 {@code RemoteMallUserService}。
	 *
	 * <p>必须用<b>非 final 字段</b>+@DubboReference（与上面的 remoteMemberLevelService 同款）：
	 * 构造器注入远程接口曾在 boot 启动失败。折扣率只做展示取优用，权威口径在订单结算侧。
	 */
	@DubboReference
	private RemoteMallUserService remoteMallUserService;

	@Override
	public AppMemberPriceVO getSkuPrices(List<String> skuIds, String userId) {
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
		String tenantId = ArynTenantContextHolder.getTenantId();
		// 同一个活动内多个 SKU 共用等级快照：按本请求缓存，避免重复 RPC
		Map<String, MemberLevelSnapshotVO> levelCache = new HashMap<>();
		boolean loggedIn = StringUtils.hasText(userId);
		MemberLevelSnapshotVO userLevel = loggedIn ? loadUserLevel(tenantId, userId, levelCache) : null;
		// 会员折扣率权益（每个请求只查一次，供 C 端计算展示取优价；未登录不查、字段为 null）
		BigDecimal userDiscountRate = loggedIn ? loadUserDiscountRate(userId) : null;
		for (PromotionActivity activity : activeActivities(tenantId)) {
			AppMemberPriceVO price = toSkuPrices(activity, wanted, tenantId, userLevel, loggedIn,
					userDiscountRate, levelCache);
			if (price != null) {
				return price;
			}
		}
		return null;
	}

	@Override
	public AppMemberPriceVO getSpuPrices(List<String> spuIds, String userId) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return null;
		}
		// 去重但保留请求顺序；列表一屏商品数有界，上限只防请求体放大
		Set<String> wantedSpuIds = new LinkedHashSet<>();
		for (String spuId : spuIds) {
			if (StringUtils.hasText(spuId) && wantedSpuIds.size() < MAX_PRICE_QUERY_SPUS) {
				wantedSpuIds.add(spuId);
			}
		}
		if (wantedSpuIds.isEmpty()) {
			return null;
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		// 同一个活动内多个 SKU 共用等级快照：按本请求缓存，避免重复 RPC
		Map<String, MemberLevelSnapshotVO> levelCache = new HashMap<>();
		boolean loggedIn = StringUtils.hasText(userId);
		MemberLevelSnapshotVO userLevel = loggedIn ? loadUserLevel(tenantId, userId, levelCache) : null;
		// 会员折扣率权益（每个请求只查一次，供 C 端计算展示取优价；未登录不查、字段为 null）
		BigDecimal userDiscountRate = loggedIn ? loadUserDiscountRate(userId) : null;
		for (PromotionActivity activity : activeActivities(tenantId)) {
			AppMemberPriceVO price = toSpuPrices(activity, wantedSpuIds, tenantId, userLevel, loggedIn,
					userDiscountRate, levelCache);
			if (price != null) {
				return price;
			}
		}
		return null;
	}

	/**
	 * 进行中的会员价活动，按优先级降序（与楼层「多活动并存取优先级最高」的展示语义一致）。
	 */
	private List<PromotionActivity> activeActivities(String tenantId) {
		LocalDateTime now = LocalDateTime.now();
		return promotionActivityMapper.selectList(Wrappers.lambdaQuery(PromotionActivity.class)
			.eq(PromotionActivity::getTenantId, tenantId)
			.eq(PromotionActivity::getActivityType, PromotionActivity.TYPE_MEMBER_PRICE)
			.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
			.le(PromotionActivity::getStartTime, now)
			.ge(PromotionActivity::getEndTime, now)
			.orderByDesc(PromotionActivity::getPriority)
			.orderByDesc(PromotionActivity::getCreateTime));
	}

	/**
	 * 单个活动的指定 SKU 会员价：
	 * 只保留「请求 SKU ∩ 活动配置 SKU ∩ 商品库存在」的部分；无交集返回 null 让调用方尝试下一个活动。
	 */
	private AppMemberPriceVO toSkuPrices(PromotionActivity activity, Set<String> wanted, String tenantId,
			MemberLevelSnapshotVO userLevel, boolean loggedIn, BigDecimal userDiscountRate,
			Map<String, MemberLevelSnapshotVO> levelCache) {
		JSONObject rules = parseRules(activity);
		if (rules == null) {
			return null;
		}
		String minLevelId = rules.getStr("minLevelId");
		if (!StringUtils.hasText(minLevelId)) {
			log.warn("会员价活动[{}]缺少 minLevelId，跳过", activity.getId());
			return null;
		}
		Map<String, BigDecimal> priceBySku = parseSkuPrices(activity, rules);
		if (priceBySku.isEmpty()) {
			return null;
		}
		// 等级门槛快照：资格与引导文案（minLevelName）都依赖它；查不到按不可用跳过，fail-closed
		MemberLevelSnapshotVO requiredLevel = loadLevel(tenantId, minLevelId, levelCache);
		if (requiredLevel == null) {
			log.warn("会员价活动[{}]等级门槛[{}]查询失败，本次展示跳过", activity.getId(), minLevelId);
			return null;
		}
		Map<String, GoodsSku> skuMap = loadSkuMap(wanted);
		List<AppMemberPriceVO.MemberSkuPriceVO> items = new ArrayList<>();
		for (String skuId : wanted) {
			BigDecimal memberPrice = priceBySku.get(skuId);
			GoodsSku sku = skuMap.get(skuId);
			if (memberPrice == null || sku == null) {
				continue;
			}
			items.add(buildItem(sku, memberPrice, requiredLevel, minLevelId));
		}
		if (items.isEmpty()) {
			return null;
		}
		AppMemberPriceVO vo = new AppMemberPriceVO();
		vo.setItems(items);
		applyEligibility(vo, userLevel, requiredLevel, loggedIn, userDiscountRate);
		return vo;
	}

	/**
	 * 单个活动的指定 SPU 会员价（商品列表卡片场景）。
	 *
	 * <p>以活动配置的 SKU 为候选集，经既有 {@link RemoteGoodsSkuService#getBySkuIds} 回填 spuId 与
	 * 当前售价后，只保留 spuId ∈ 请求集合的 SKU；无交集返回 null 让调用方尝试下一个活动。
	 *
	 * <p><b>为什么用 getBySkuIds 而不是新增「按 SPU 查会员价」的 Dubbo 接口 / 商品库 SQL</b>：
	 * 会员价配在 SKU 上、只对活动配置的 SKU 有意义，配置集合必然已包含可展示的 SKU；
	 * 用既有批量接口反查 spuId 即可，避免为列表聚合扩大 product 域接口面（cloud 模式下还要
	 * 多维护一个跨域契约与双模式适配）。
	 *
	 * <p><b>为什么同一 SPU 返回多条 item 而不做挑选</b>：列表卡片展示的是 SPU 聚合价
	 * （goods_spu.sales_price = min(SKU 售价)），只有 C 端知道卡片当前展示价对应哪个 SKU；
	 * 后端若按「取最低价」挑一个，多规格场景会给出与卡片展示不一致的价格。
	 * 故这里逐 SKU 全量下发，由 C 端按卡片展示价对应的 SKU 精确匹配。
	 */
	private AppMemberPriceVO toSpuPrices(PromotionActivity activity, Set<String> wantedSpuIds, String tenantId,
			MemberLevelSnapshotVO userLevel, boolean loggedIn, BigDecimal userDiscountRate,
			Map<String, MemberLevelSnapshotVO> levelCache) {
		JSONObject rules = parseRules(activity);
		if (rules == null) {
			return null;
		}
		String minLevelId = rules.getStr("minLevelId");
		if (!StringUtils.hasText(minLevelId)) {
			log.warn("会员价活动[{}]缺少 minLevelId，跳过", activity.getId());
			return null;
		}
		Map<String, BigDecimal> priceBySku = parseSkuPrices(activity, rules);
		if (priceBySku.isEmpty()) {
			return null;
		}
		// 配置 SKU 即候选集合；超限截断（静默忽略，只影响价格标注，真享价以结算链为准）
		List<String> configuredSkuIds = priceBySku.keySet().stream().limit(MAX_CONFIGURED_SKUS).toList();
		MemberLevelSnapshotVO requiredLevel = loadLevel(tenantId, minLevelId, levelCache);
		if (requiredLevel == null) {
			log.warn("会员价活动[{}]等级门槛[{}]查询失败，本次展示跳过", activity.getId(), minLevelId);
			return null;
		}
		Map<String, GoodsSku> skuMap = loadSkuMap(configuredSkuIds);
		List<AppMemberPriceVO.MemberSkuPriceVO> items = new ArrayList<>();
		for (String skuId : configuredSkuIds) {
			GoodsSku sku = skuMap.get(skuId);
			if (sku == null || !StringUtils.hasText(sku.getSpuId()) || !wantedSpuIds.contains(sku.getSpuId())) {
				continue;
			}
			items.add(buildItem(sku, priceBySku.get(skuId), requiredLevel, minLevelId));
		}
		if (items.isEmpty()) {
			return null;
		}
		AppMemberPriceVO vo = new AppMemberPriceVO();
		vo.setItems(items);
		applyEligibility(vo, userLevel, requiredLevel, loggedIn, userDiscountRate);
		return vo;
	}

	/** 解析活动 rules；损坏时告警并返回 null（该活动跳过，不阻断展示）。 */
	private JSONObject parseRules(PromotionActivity activity) {
		try {
			return JSONUtil.parseObj(activity.getRules());
		}
		catch (Exception ex) {
			log.warn("会员价活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			return null;
		}
	}

	/** 构造单条会员价商品项（SKU/SPU 与当前售价来自商品库，会员价来自活动配置）。 */
	private AppMemberPriceVO.MemberSkuPriceVO buildItem(GoodsSku sku, BigDecimal memberPrice,
			MemberLevelSnapshotVO requiredLevel, String minLevelId) {
		AppMemberPriceVO.MemberSkuPriceVO item = new AppMemberPriceVO.MemberSkuPriceVO();
		item.setSkuId(sku.getId());
		item.setSpuId(sku.getSpuId());
		item.setMemberPrice(memberPrice);
		item.setOriginalPrice(sku.getSalesPrice());
		item.setMinLevelId(requiredLevel.getLevelId() != null ? requiredLevel.getLevelId() : minLevelId);
		item.setMinLevelName(requiredLevel.getLevelName());
		return item;
	}

	/**
	 * 回填资格三态与会员折扣率（两个查询入口共用，避免口径漂移）：
	 * 未登录不下发等级/资格/折扣率（均 null，展示端按营销引导处理）；
	 * 登录态下发用户等级，资格按 sortOrder 判定（无等级/远程异常一律不合格，fail-closed），
	 * 折扣率按权益值下发（1=无折扣权益；远程异常已由 {@link #loadUserDiscountRate} 回落 1）。
	 * 资格与成交价最终仍以下单链路硬校验 / 订单结算为准。
	 *
	 * <p>userDiscountRate 只用于展示取优：展示价 = min(memberPrice, originalPrice × userDiscountRate)，
	 * 避免 C 端把会员折扣率误当成会员价的活动价（两套体系，结算侧按同一取优口径成交）。
	 */
	private void applyEligibility(AppMemberPriceVO vo, MemberLevelSnapshotVO userLevel,
			MemberLevelSnapshotVO requiredLevel, boolean loggedIn, BigDecimal userDiscountRate) {
		if (!loggedIn) {
			return;
		}
		vo.setUserLevelId(userLevel != null ? userLevel.getLevelId() : null);
		vo.setUserLevelName(userLevel != null ? userLevel.getLevelName() : null);
		vo.setEligible(meetsLevel(userLevel, requiredLevel));
		// 登录用户必有值：loadUserDiscountRate 的异常/非法值已回落 1，不会下发 null
		vo.setUserDiscountRate(userDiscountRate != null ? userDiscountRate : BigDecimal.ONE);
	}

	/**
	 * 登录用户会员折扣率权益（member_benefit benefitType=1，如 0.9 = 9 折），每个请求只查一次。
	 *
	 * <p>取值口径：rate ∈ (0,1] 才采用；为空 / ≤0 / &gt;1 一律回落 1（无折扣权益）。
	 * 查询异常按 1 处理并 warn —— <b>展示侧 fail-open 到「不打折」</b>，不能因权益查询失败
	 * 阻断列表/商详渲染；会员价与折扣率的权威取优口径在订单结算侧，本值只供 C 端算展示取优价。
	 */
	private BigDecimal loadUserDiscountRate(String userId) {
		try {
			MemberBenefitsVO benefits = remoteMallUserService.getMemberBenefits(userId);
			BigDecimal rate = benefits != null ? benefits.getDiscountRate() : null;
			if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
				return BigDecimal.ONE;
			}
			return rate;
		}
		catch (Exception ex) {
			log.warn("会员价展示查询用户[{}]折扣率权益失败，按无折扣处理: {}", userId, ex.getMessage());
			return BigDecimal.ONE;
		}
	}

	/** 会员价资格：用户等级 sortOrder ≥ 活动门槛等级 sortOrder；任一缺失按不合格（fail-closed）。 */
	private boolean meetsLevel(MemberLevelSnapshotVO userLevel, MemberLevelSnapshotVO requiredLevel) {
		if (userLevel == null || userLevel.getSortOrder() == null || requiredLevel.getSortOrder() == null) {
			return false;
		}
		return userLevel.getSortOrder() >= requiredLevel.getSortOrder();
	}

	/**
	 * 用户当前等级快照：异常按无等级返回（fail-closed），结果按请求缓存（含 null，避免重复 RPC）。
	 */
	private MemberLevelSnapshotVO loadUserLevel(String tenantId, String userId,
			Map<String, MemberLevelSnapshotVO> cache) {
		String cacheKey = "user:" + userId;
		if (cache.containsKey(cacheKey)) {
			return cache.get(cacheKey);
		}
		MemberLevelSnapshotVO level = null;
		try {
			level = remoteMemberLevelService.getUserLevel(tenantId, userId);
		}
		catch (Exception ex) {
			log.warn("会员价展示查询用户[{}]会员等级失败，按无等级处理: {}", userId, ex.getMessage());
		}
		cache.put(cacheKey, level);
		return level;
	}

	/**
	 * 活动门槛等级快照：异常按不可用返回（fail-closed），结果按请求缓存（含 null，避免重复 RPC）。
	 */
	private MemberLevelSnapshotVO loadLevel(String tenantId, String levelId, Map<String, MemberLevelSnapshotVO> cache) {
		String cacheKey = "level:" + levelId;
		if (cache.containsKey(cacheKey)) {
			return cache.get(cacheKey);
		}
		MemberLevelSnapshotVO level = null;
		try {
			level = remoteMemberLevelService.getLevelById(tenantId, levelId);
		}
		catch (Exception ex) {
			log.warn("会员价展示查询等级[{}]失败，按不可用处理: {}", levelId, ex.getMessage());
		}
		cache.put(cacheKey, level);
		return level;
	}

	/**
	 * rules.skus 的 SKU→会员价映射（保持配置顺序）；unitPrice 非正数的条目直接忽略。
	 */
	private Map<String, BigDecimal> parseSkuPrices(PromotionActivity activity, JSONObject rules) {
		Map<String, BigDecimal> priceBySku = new LinkedHashMap<>();
		JSONArray skus = rules.getJSONArray("skus");
		if (skus == null) {
			return priceBySku;
		}
		for (Object element : skus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal unitPrice = sku.getBigDecimal("unitPrice");
			if (StringUtils.hasText(skuId) && unitPrice != null && unitPrice.signum() > 0) {
				priceBySku.put(skuId, unitPrice);
			}
		}
		return priceBySku;
	}

	private Map<String, GoodsSku> loadSkuMap(Collection<String> skuIds) {
		try {
			return remoteGoodsSkuService.getBySkuIds(new ArrayList<>(skuIds))
				.stream()
				.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		}
		catch (Exception ex) {
			log.warn("会员价查询商品失败, skuCount={}", skuIds.size(), ex);
			return Map.of();
		}
	}

}
