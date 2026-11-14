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
		validateCalendar(calendar);
		calendar.setId(null);
		calendar.setTenantId(tenantId);
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
	 * 划线基准取 SKU 实时售价，远程失败按无划线价降级，不阻断楼层。
	 */
	public List<AppCalendarSpecialVO> specials(String tenantId, String calendarId) {
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
		Map<String, java.math.BigDecimal> salesPriceBySku = salesPrices(skuIds);
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
			items.forEach(item -> item.setOriginalPrice(salesPriceBySku.get(item.getSkuId())));
			vo.setItems(items);
			result.add(vo);
		}
		return result;
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

	/** 档期窗二次校验（活动在窗内但档期停用/未开始时剔除）。 */
	private List<PromotionActivity> filterByCalendarWindow(String tenantId, List<PromotionActivity> activities,
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

	private Map<String, java.math.BigDecimal> salesPrices(List<String> skuIds) {
		if (CollectionUtils.isEmpty(skuIds)) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.filter(sku -> sku.getSalesPrice() != null)
				.collect(Collectors.toMap(com.aryn.cloud.product.api.entity.GoodsSku::getId,
						com.aryn.cloud.product.api.entity.GoodsSku::getSalesPrice, (l, r) -> l));
		}
		catch (Exception ex) {
			log.warn("档期特供划线价查询失败，按无划线价降级: {}", ex.getMessage());
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
		validateWindow(calendar);
	}

	private void validateWindow(PromoCalendar calendar) {
		if (calendar.getStartTime() == null || calendar.getEndTime() == null
				|| calendar.getEndTime().isBefore(calendar.getStartTime())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("档期时间窗非法");
		}
	}

}
