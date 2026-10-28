package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.MemberActivityGoodsVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.IMemberActivityGoodsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员专区自动数据源实现：会员价活动（promotion_activity type=10）→ 商品 SPU 列表。
 *
 * <p>口径与契约：
 * <ol>
 *   <li><b>活动选择</b>：未指定 activityId 时取「已发布 + 当前在时间窗内」的 type=10 活动，
 *       priority desc、create_time desc 第一个；指定 activityId 时只校验存在 + type=10
 *       （del_flag 由 MyBatis-Plus 逻辑删除过滤），<b>不校验时间窗</b>——允许运营提前装修，
 *       活动未开始/已结束时价格接口自然不返回活动价，商品列表仍可展示；</li>
 *   <li><b>商品回填</b>：解析 rules.skus[].skuId（保持配置顺序），复用既有
 *       {@link RemoteGoodsSkuService#getBySkuIds} 批量回填 spuId；查不到的 SKU 跳过
 *       （商品下架/删除不应让整个专区空掉）；</li>
 *   <li><b>去重保序</b>：同一 SPU 多个规格只保留首次出现位置（商品卡按 SPU 展示）；</li>
 *   <li><b>上限保护</b>：SKU 100 / SPU 50，超限静默截断（专区一屏商品有限，只防配置异常放大请求）；</li>
 *   <li><b>失败降级</b>：商品 RPC 异常返回 null（C 端楼层退化为不渲染），不 500。</li>
 * </ol>
 *
 * <p>不含任何用户维度数据（免登）：价格由 {@code /app/member/prices} 单独查询，
 * 本服务只负责「活动 → SPU 列表」的搬运，避免与价格聚合口径重复。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberActivityGoodsServiceImpl implements IMemberActivityGoodsService {

	/** 单次解析的活动配置 SKU 上限（与价格接口的批量查询保护同量级，防异常配置放大请求）。 */
	private static final int MAX_ACTIVITY_SKUS = 100;

	/** 单次返回的 SPU 上限（专区 Tab 一屏商品数远小于该值）。 */
	private static final int MAX_ACTIVITY_SPUS = 50;

	private final PromotionActivityMapper promotionActivityMapper;

	/**
	 * 商品批量回填走 product 域 Dubbo 接口。
	 *
	 * <p>必须用<b>非 final 字段</b>+@DubboReference：构造器注入远程接口曾在 boot 启动失败
	 * （与 MemberPriceActivityServiceImpl 的 remoteMemberLevelService 同款踩坑）。
	 */
	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Override
	public MemberActivityGoodsVO getActivityGoods(String activityId) {
		PromotionActivity activity = resolveActivity(activityId);
		if (activity == null) {
			return null;
		}
		List<String> skuIds = parseSkuIds(activity);
		if (skuIds.isEmpty()) {
			return null;
		}
		Map<String, GoodsSku> skuMap = loadSkuMap(skuIds);
		if (skuMap.isEmpty()) {
			return null;
		}
		// 按活动配置顺序回填 spuId 并去重保序（同一 SPU 多规格只留首次出现位置）
		Set<String> spuIds = new LinkedHashSet<>();
		for (String skuId : skuIds) {
			GoodsSku sku = skuMap.get(skuId);
			if (sku == null || !StringUtils.hasText(sku.getSpuId())) {
				continue;
			}
			if (spuIds.size() >= MAX_ACTIVITY_SPUS) {
				break;
			}
			spuIds.add(sku.getSpuId());
		}
		if (spuIds.isEmpty()) {
			return null;
		}
		MemberActivityGoodsVO vo = new MemberActivityGoodsVO();
		vo.setActivityId(activity.getId());
		vo.setActivityName(activity.getActivityName());
		vo.setEndTime(activity.getEndTime());
		vo.setSpuIds(new ArrayList<>(spuIds));
		return vo;
	}

	/**
	 * 解析目标活动：
	 * 指定 activityId → 按 ID 查（存在 + type=10，不校验时间窗，允许提前装修）；
	 * 未指定 → 当前进行中的会员价活动（已发布 + 时间窗内，priority desc、create_time desc 第一个）。
	 */
	private PromotionActivity resolveActivity(String activityId) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (StringUtils.hasText(activityId)) {
			PromotionActivity activity = promotionActivityMapper.selectOne(
					Wrappers.lambdaQuery(PromotionActivity.class)
						.eq(PromotionActivity::getTenantId, tenantId)
						.eq(PromotionActivity::getId, activityId.trim()));
			if (activity == null || !PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
				// 不存在或不是会员价活动：装修数据源视为不可用（不抛异常，C 端楼层不渲染）
				log.warn("会员专区数据源指定活动[{}]不存在或非会员价类型，返回空", activityId);
				return null;
			}
			return activity;
		}
		LocalDateTime now = LocalDateTime.now();
		List<PromotionActivity> active = promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
					.eq(PromotionActivity::getTenantId, tenantId)
					.eq(PromotionActivity::getActivityType, PromotionActivity.TYPE_MEMBER_PRICE)
					.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.le(PromotionActivity::getStartTime, now)
					.ge(PromotionActivity::getEndTime, now)
					.orderByDesc(PromotionActivity::getPriority)
					.orderByDesc(PromotionActivity::getCreateTime));
		if (active == null || active.isEmpty()) {
			return null;
		}
		return active.get(0);
	}

	/** 解析 rules.skus[].skuId（保持配置顺序、去重、上限 MAX_ACTIVITY_SKUS）。 */
	private List<String> parseSkuIds(PromotionActivity activity) {
		Set<String> skuIds = new LinkedHashSet<>();
		try {
			JSONObject rules = JSONUtil.parseObj(activity.getRules());
			JSONArray skus = rules.getJSONArray("skus");
			if (skus == null) {
				return List.of();
			}
			for (Object element : skus) {
				if (skuIds.size() >= MAX_ACTIVITY_SKUS) {
					break;
				}
				String skuId = ((JSONObject) element).getStr("skuId");
				if (StringUtils.hasText(skuId)) {
					skuIds.add(skuId);
				}
			}
		}
		catch (Exception ex) {
			log.warn("会员专区数据源活动[{}]规则解析失败，返回空: {}", activity.getId(), ex.getMessage());
			return List.of();
		}
		return new ArrayList<>(skuIds);
	}

	/**
	 * 批量回填商品（失败降级为空映射）：商品下架/删除的 SKU 跳过，
	 * RPC 异常返回空映射由调用方返回 null（C 端楼层不渲染，不阻断页面）。
	 */
	private Map<String, GoodsSku> loadSkuMap(List<String> skuIds) {
		try {
			List<GoodsSku> goods = remoteGoodsSkuService.getBySkuIds(skuIds);
			if (goods == null || goods.isEmpty()) {
				return Map.of();
			}
			return goods.stream()
				.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (left, right) -> left));
		}
		catch (Exception ex) {
			log.warn("会员专区数据源查询商品失败, skuCount={}", skuIds.size(), ex);
			return Map.of();
		}
	}

}
