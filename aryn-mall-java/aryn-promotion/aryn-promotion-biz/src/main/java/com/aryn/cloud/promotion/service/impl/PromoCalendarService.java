package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.entity.PromoCalendar;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.AppCalendarSpecialVO;
import com.aryn.cloud.promotion.mapper.PromoCalendarMapper;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购档期服务：管理端 CRUD + C 端生效档期查询。
 *
 * <p>档期是 B2B 经营节奏锚点（周度/靠港/自然月/自定义窗）；活动经
 * promotion_activity.calendar_id 归属档期，引擎在试算时过滤未生效档期
 * （{@code PromotionEngineServiceImpl#filterByCalendar}）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCalendarService {

	private final PromoCalendarMapper promoCalendarMapper;

	private final PromotionActivityMapper promotionActivityMapper;

	/** 商品售价远程接口（特供价划线基准）；非 final + @DubboReference（构造器注入远程接口曾在 boot 启动失败）。 */
	@DubboReference
	private com.aryn.cloud.product.api.remote.RemoteGoodsSkuService remoteGoodsSkuService;

	/** 靠港计划远程接口（anchor_type=2 档期窗口=eta~etd）；非 final + @DubboReference。 */
	@DubboReference
	private com.aryn.cloud.vessel.api.remote.RemoteVesselService remoteVesselService;

	/** 档期列表（管理端，含全部状态）。 */
	public List<PromoCalendar> list(String tenantId, String status, String keyword) {
		return promoCalendarMapper.selectList(Wrappers.lambdaQuery(PromoCalendar.class)
			.eq(PromoCalendar::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), PromoCalendar::getStatus, status)
			.like(StringUtils.hasText(keyword), PromoCalendar::getCalendarName, keyword)
			.orderByDesc(PromoCalendar::getStartTime));
	}

	public PromoCalendar get(String tenantId, String id) {
		return promoCalendarMapper.selectOne(Wrappers.lambdaQuery(PromoCalendar.class)
			.eq(PromoCalendar::getTenantId, tenantId)
			.eq(PromoCalendar::getId, id));
	}

	/** 新建档期。 */
	@Transactional(rollbackFor = Exception.class)
	public PromoCalendar save(String tenantId, PromoCalendar calendar) {
		calendar.setId(null);
		calendar.setTenantId(tenantId);
		validateCalendar(calendar);
		promoCalendarMapper.insert(calendar);
		return calendar;
	}

	/** 修改档期；已生效档期允许调整时间窗（影响其下活动的参与窗口，运营自主决定）。 */
	@Transactional(rollbackFor = Exception.class)
	public PromoCalendar update(String tenantId, String id, PromoCalendar calendar) {
		PromoCalendar existing = get(tenantId, id);
		if (existing == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("档期不存在");
		}
		validateCalendar(calendar);
		calendar.setId(id);
		calendar.setTenantId(tenantId);
		promoCalendarMapper.updateById(calendar);
		return get(tenantId, id);
	}

	/** 状态切换（0草稿/1生效/2停用）。 */
	@Transactional(rollbackFor = Exception.class)
	public PromoCalendar changeStatus(String tenantId, String id, String targetStatus) {
		if (!PromoCalendar.STATUS_ACTIVE.equals(targetStatus)
				&& !PromoCalendar.STATUS_DISABLED.equals(targetStatus)
				&& !PromoCalendar.STATUS_DRAFT.equals(targetStatus)) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("非法的目标状态");
		}
		PromoCalendar existing = get(tenantId, id);
		if (existing == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("档期不存在");
		}
		if (PromoCalendar.STATUS_ACTIVE.equals(targetStatus)) {
			validateWindow(existing);
		}
		existing.setStatus(targetStatus);
		promoCalendarMapper.updateById(existing);
		return existing;
	}

	/** 逻辑删除档期（其下活动因档期失效自动退出试算，活动本身不动）。 */
	@Transactional(rollbackFor = Exception.class)
	public void delete(String tenantId, String id) {
		if (get(tenantId, id) == null) {
			return;
		}
		promoCalendarMapper.deleteById(id);
	}

	/** C 端生效档期（status=1 且当前时间在窗口内，按开始时间升序）。 */
	public List<PromoCalendar> effectiveCalendars(String tenantId) {
		LocalDateTime now = LocalDateTime.now();
		return promoCalendarMapper.selectList(Wrappers.lambdaQuery(PromoCalendar.class)
			.eq(PromoCalendar::getTenantId, tenantId)
			.eq(PromoCalendar::getStatus, PromoCalendar.STATUS_ACTIVE)
			.le(PromoCalendar::getStartTime, now)
			.ge(PromoCalendar::getEndTime, now)
			.orderByAsc(PromoCalendar::getStartTime));
	}

	/**
	 * C 端档期特供列表：指定档期（或全部生效档期）下进行中的 type=12 活动。
	 *
	 * <p>展示口径与成交口径同源：只展示已发布且时间窗覆盖当前的活动，剩余限量按
	 * total_quota - consumed_quota（限量熔断已由引擎与 reserve 原子扣减保证）；
	 * 限量耗尽的活动前置剔除（展示价=可成交价，方案 §4.1）；按购买场景/船舶圈定
	 * 过滤（与引擎 matchesScope 同口径，2026-11-15 审查修复）。
	 * 划线基准取 SKU 实时售价，远程失败按无划线价降级，不阻断楼层。
	 * @param purchaseScene 当前购买场景（空=不过滤场景维度）
	 * @param vesselId 当前船舶上下文（空=船舶圈定活动不展示，fail-closed）
	 */
	public List<AppCalendarSpecialVO> specials(String tenantId, String calendarId, String purchaseScene,
			String vesselId) {
		return specialsCore(tenantId, calendarId, purchaseScene, vesselId, true);
	}

	/**
	 * 价格快照 Job 专用：不做场景/船舶圈定过滤（快照口径取全部进行中特供价的最低价，
	 * 方案 §11.4「档期特供价应入 goods_sku_price_daily」），窗口/档期/限量熔断口径不变。
	 */
	public List<AppCalendarSpecialVO> specialsForSnapshot(String tenantId) {
		return specialsCore(tenantId, null, null, null, false);
	}

	private List<AppCalendarSpecialVO> specialsCore(String tenantId, String calendarId, String purchaseScene,
			String vesselId, boolean applyScopeFilter) {
		LocalDateTime now = LocalDateTime.now();
		List<PromotionActivity> activities = promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
					.eq(PromotionActivity::getTenantId, tenantId)
					.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.eq(PromotionActivity::getActivityType, PromotionActivity.TYPE_CALENDAR_SPECIAL)
					.le(PromotionActivity::getStartTime, now)
					.ge(PromotionActivity::getEndTime, now)
					.eq(StringUtils.hasText(calendarId), PromotionActivity::getCalendarId, calendarId));
		if (activities.isEmpty()) {
			return List.of();
		}
		activities = filterByCalendarWindow(tenantId, activities, now);
		activities = activities.stream()
			.filter(activity -> !quotaExhausted(activity))
			.filter(activity -> !applyScopeFilter || matchesDisplayScope(activity, purchaseScene, vesselId))
			.toList();
		if (activities.isEmpty()) {
			return List.of();
		}
		List<String> calendarIds = activities.stream()
			.map(PromotionActivity::getCalendarId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, String> calendarNameById = calendarIds.isEmpty() ? Map.of()
				: promoCalendarMapper.selectList(Wrappers.lambdaQuery(PromoCalendar.class)
					.eq(PromoCalendar::getTenantId, tenantId)
					.in(PromoCalendar::getId, calendarIds))
				.stream()
				.collect(Collectors.toMap(PromoCalendar::getId, PromoCalendar::getCalendarName, (l, r) -> l));
		List<String> skuIds = activities.stream()
			.flatMap(activity -> parseSpecialSkus(activity).stream())
			.map(AppCalendarSpecialVO.SpecialItem::getSkuId)
			.distinct()
			.toList();
		// 一次批量取 SKU 展示信息：划线基准 + 商品名/图（楼层卡片渲染），失败降级仅本地价
		Map<String, com.aryn.cloud.product.api.entity.GoodsSku> skuById = fetchSkus(skuIds);
		List<AppCalendarSpecialVO> result = new ArrayList<>(activities.size());
		for (PromotionActivity activity : activities) {
			AppCalendarSpecialVO vo = new AppCalendarSpecialVO();
			vo.setActivityId(activity.getId());
			vo.setActivityName(activity.getActivityName());
			vo.setCalendarId(activity.getCalendarId());
			vo.setCalendarName(calendarNameById.get(activity.getCalendarId()));
			vo.setEndTime(activity.getEndTime());
			vo.setTotalQuota(activity.getTotalQuota());
			if (activity.getTotalQuota() != null && activity.getTotalQuota() > 0) {
				int used = activity.getConsumedQuota() == null ? 0 : activity.getConsumedQuota();
				vo.setRemainingQuota(Math.max(activity.getTotalQuota() - used, 0));
			}
			List<AppCalendarSpecialVO.SpecialItem> items = parseSpecialSkus(activity);
			for (AppCalendarSpecialVO.SpecialItem item : items) {
				com.aryn.cloud.product.api.entity.GoodsSku sku = skuById.get(item.getSkuId());
				if (sku == null) {
					continue;
				}
				item.setOriginalPrice(sku.getSalesPrice());
				item.setPicUrl(sku.getPicUrl());
				// 楼层卡片点击跳商详要靠 spuId；此前漏回填导致 C 端整层「点不动」
				item.setSpuId(StringUtils.hasText(sku.getSpuId()) ? sku.getSpuId()
						: (sku.getGoodsSpu() == null ? null : sku.getGoodsSpu().getId()));
				if (sku.getGoodsSpu() != null) {
					item.setSpuName(sku.getGoodsSpu().getName());
				}
			}
			vo.setItems(items);
			result.add(vo);
		}
		return result;
	}

	/** 限量耗尽前置剔除：total_quota 空/0 不限；已消耗 ≥ 限量则楼层不再展示（与引擎熔断同口径）。 */
	private boolean quotaExhausted(PromotionActivity activity) {
		if (activity.getTotalQuota() == null || activity.getTotalQuota() <= 0) {
			return false;
		}
		int used = activity.getConsumedQuota() == null ? 0 : activity.getConsumedQuota();
		return used >= activity.getTotalQuota();
	}

	/**
	 * 展示侧圈定过滤（与引擎 matchesScope 同口径的楼层子集）：
	 * purchase_scene 精确匹配；scope_type 1 全场 / 2 指定 SKU（楼层按活动配置展示）/
	 * 4 场景圈定 / 5 船舶圈定；6 靠港 / 7 港口 / 8 常购楼层无对应上下文，不展示（fail-closed）。
	 */
	boolean matchesDisplayScope(PromotionActivity activity, String purchaseScene, String vesselId) {
		if (StringUtils.hasText(activity.getPurchaseScene())
				&& !activity.getPurchaseScene().equals(purchaseScene)) {
			return false;
		}
		String scopeType = activity.getScopeType() == null ? "1" : activity.getScopeType();
		return switch (scopeType) {
			case "1", "2" -> true;
			case "4" -> activity.getScopeValue() != null && activity.getScopeValue().equals(purchaseScene);
			case "5" -> StringUtils.hasText(vesselId) && activity.getScopeValue() != null
					&& java.util.Arrays.asList(activity.getScopeValue().split(",")).contains(vesselId);
			default -> false;
		};
	}

	private List<AppCalendarSpecialVO.SpecialItem> parseSpecialSkus(PromotionActivity activity) {
		List<AppCalendarSpecialVO.SpecialItem> items = new ArrayList<>();
		try {
			JSONArray skus = JSONUtil.parseObj(activity.getRules()).getJSONArray("skus");
			if (skus == null) {
				return items;
			}
			for (Object element : skus) {
				JSONObject sku = (JSONObject) element;
				String skuId = sku.getStr("skuId");
				java.math.BigDecimal price = sku.getBigDecimal("price");
				if (StringUtils.hasText(skuId) && price != null && price.signum() > 0) {
					AppCalendarSpecialVO.SpecialItem item = new AppCalendarSpecialVO.SpecialItem();
					item.setSkuId(skuId);
					item.setPrice(price);
					item.setPerUserLimit(activity.getPerUserLimit());
					items.add(item);
				}
			}
		}
		catch (Exception ex) {
			log.warn("档期特供活动[{}]规则解析失败，按空商品返回", activity.getId());
		}
		return items;
	}

	/** 档期窗二次校验（活动在窗内但档期停用/未开始时剔除）；包内共享（阶梯价展示同口径）。 */
	List<PromotionActivity> filterByCalendarWindow(String tenantId, List<PromotionActivity> activities,
			LocalDateTime now) {
		List<String> calendarIds = activities.stream()
			.map(PromotionActivity::getCalendarId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (calendarIds.isEmpty()) {
			return activities;
		}
		List<String> effective = promoCalendarMapper.selectList(Wrappers.lambdaQuery(PromoCalendar.class)
				.eq(PromoCalendar::getTenantId, tenantId)
				.eq(PromoCalendar::getStatus, PromoCalendar.STATUS_ACTIVE)
				.le(PromoCalendar::getStartTime, now)
				.ge(PromoCalendar::getEndTime, now)
				.in(PromoCalendar::getId, calendarIds))
			.stream()
			.map(PromoCalendar::getId)
			.toList();
		return activities.stream()
			.filter(activity -> !StringUtils.hasText(activity.getCalendarId())
					|| effective.contains(activity.getCalendarId()))
			.toList();
	}

	private Map<String, com.aryn.cloud.product.api.entity.GoodsSku> fetchSkus(List<String> skuIds) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.collect(Collectors.toMap(com.aryn.cloud.product.api.entity.GoodsSku::getId,
						sku -> sku, (l, r) -> l));
		}
		catch (Exception ex) {
			log.warn("档期特供商品信息查询失败，按无划线价/无图降级: {}", ex.getMessage());
			return Map.of();
		}
	}

	private void validateCalendar(PromoCalendar calendar) {
		if (!StringUtils.hasText(calendar.getCalendarName())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("档期名称不能为空");
		}
		if (!PromoCalendar.ANCHOR_WEEKLY.equals(calendar.getAnchorType())
				&& !PromoCalendar.ANCHOR_VESSEL_CALL.equals(calendar.getAnchorType())
				&& !PromoCalendar.ANCHOR_MONTHLY.equals(calendar.getAnchorType())
				&& !PromoCalendar.ANCHOR_CUSTOM.equals(calendar.getAnchorType())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("非法的锚点类型");
		}
		if (PromoCalendar.ANCHOR_VESSEL_CALL.equals(calendar.getAnchorType())) {
			applyVesselCallWindow(calendar);
		}
		validateWindow(calendar);
	}

	/**
	 * 靠港档期窗口派生（方案 §4.4：anchor_type=2 窗口=vessel_call 的 eta~etd）。
	 * 2026-11-15 审查修复：此前 anchorRef 只存不校验、时间窗与靠港计划脱钩。
	 * 派生失败（计划不存在/无 eta~etd/远程异常）直接拒绝保存，不允许手填窗口冒充靠港档期。
	 */
	private void applyVesselCallWindow(PromoCalendar calendar) {
		if (!StringUtils.hasText(calendar.getAnchorRef())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("靠港档期必须关联靠港计划");
		}
		com.aryn.cloud.vessel.api.dto.VesselContextDTO call;
		try {
			call = remoteVesselService.getVesselCallContext(calendar.getTenantId(), calendar.getAnchorRef());
		}
		catch (Exception ex) {
			log.warn("靠港计划查询失败 anchorRef={}", calendar.getAnchorRef(), ex);
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("靠港计划查询失败，请稍后重试");
		}
		if (call == null || call.getEta() == null || call.getEtd() == null || call.getEtd().isBefore(call.getEta())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("靠港计划不存在或缺少 ETA/ETD 窗口");
		}
		calendar.setStartTime(call.getEta());
		calendar.setEndTime(call.getEtd());
	}

	private void validateWindow(PromoCalendar calendar) {
		if (calendar.getStartTime() == null || calendar.getEndTime() == null
				|| calendar.getEndTime().isBefore(calendar.getStartTime())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("档期时间窗非法");
		}
	}

}
