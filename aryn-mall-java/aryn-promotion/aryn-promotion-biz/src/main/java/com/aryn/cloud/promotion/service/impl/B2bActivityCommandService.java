package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.B2bActivitySaveDTO;
import com.aryn.cloud.promotion.api.entity.PromoCalendar;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromoCalendarMapper;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 手工表单、复制与活动包共用命令。写入只接受可编辑字段，配置用 revision 防并发覆盖。 */
@Service
@RequiredArgsConstructor
public class B2bActivityCommandService {

	private final PromotionActivityMapper mapper;
	private final ActivityPublishGovernanceService governance;
	private final PromoCalendarMapper calendarMapper;

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	public PromotionActivity get(String id) {
		PromotionActivity activity = mapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
			.eq(PromotionActivity::getTenantId, tenant())
			.eq(PromotionActivity::getId, id));
		if (activity == null || !tenant().equals(activity.getTenantId())) {
			throw new ArynBusinessException("活动不存在");
		}
		return activity;
	}

	@Transactional(rollbackFor = Exception.class)
	public PromotionActivity create(B2bActivitySaveDTO dto) {
		PromotionActivity activity = editable(dto);
		B2bActivityAdmission.requireManual(activity);
		activity.setTenantId(tenant());
		validate(activity);
		activity.setStatus(PromotionActivity.STATUS_DRAFT);
		activity.setConsumedBudget(BigDecimal.ZERO);
		activity.setConsumedQuota(0);
		activity.setRevision(0L);
		activity.setCreateTime(LocalDateTime.now());
		activity.setDelFlag("0");
		mapper.insert(activity);
		return activity;
	}

	@Transactional(rollbackFor = Exception.class)
	public PromotionActivity update(String id, B2bActivitySaveDTO dto) {
		PromotionActivity existing = writable(id, dto.getExpectedRevision());
		PromotionActivity activity = editable(dto);
		if (!StringUtils.hasText(activity.getActivityType())) {
			activity.setActivityType(existing.getActivityType());
		}
		B2bActivityAdmission.requireManual(activity);
		if (!Objects.equals(existing.getActivityType(), activity.getActivityType())) {
			throw new ArynBusinessException("活动类型不可变更，请新建草稿");
		}
		if (PromotionActivity.STATUS_PUBLISHED.equals(existing.getStatus())) {
			throw new ArynBusinessException("已发布活动不可修改，请先暂停或新建版本");
		}
		activity.setId(id);
		activity.setTenantId(tenant());
		activity.setStatus(existing.getStatus());
		activity.setConsumedBudget(existing.getConsumedBudget());
		activity.setConsumedQuota(existing.getConsumedQuota());
		validate(activity);
		persist(activity, dto.getExpectedRevision());
		return activity;
	}

	@Transactional(rollbackFor = Exception.class)
	public PromotionActivity copy(String id, Long expectedRevision) {
		PromotionActivity source = writable(id, expectedRevision);
		B2bActivitySaveDTO dto = new B2bActivitySaveDTO();
		BeanUtils.copyProperties(source, dto);
		dto.setActivityName(source.getActivityName() + "（副本）");
		return create(dto);
	}

	public List<PromotionActivity> conflicts(String id) {
		PromotionActivity activity = get(id);
		B2bActivityAdmission.requireManual(activity);
		return governance.listPublishConflicts(tenant(), activity);
	}

	@Transactional(rollbackFor = Exception.class)
	public void publish(String id, Long expectedRevision, boolean force) {
		PromotionActivity activity = writable(id, expectedRevision);
		validate(activity);
		assertLadderPricesBelowSales(activity);
		publishValidated(activity, expectedRevision, force);
	}

	/** 活动包外层已在事务外捕获授权商品事实，提交时只访问本库。无HTTP/RPC入口。 */
	@Transactional(rollbackFor = Exception.class)
	public void publishCampaign(String id, Long expectedRevision, boolean force, Map<String, BigDecimal> sales) {
		PromotionActivity activity = writable(id, expectedRevision);
		validate(activity);
		assertLadderPricesBelowSales(activity, sales);
		publishValidated(activity, expectedRevision, force);
	}

	private void publishValidated(PromotionActivity activity, Long expectedRevision, boolean force) {
		if (!activity.getEndTime().isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("活动结束时间已过，请先调整活动时间再发布");
		}
		if (!force) {
			List<PromotionActivity> conflicts = governance.listPublishConflicts(tenant(), activity);
			if (!conflicts.isEmpty()) {
				throw new ArynBusinessException("与已发布活动时间重叠：" + conflicts.stream()
					.map(PromotionActivity::getActivityName).toList() + "；运行时同类取最优，确认后可强制发布");
			}
		}
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setPublishTime(LocalDateTime.now());
		persist(activity, expectedRevision);
	}

	@Transactional(rollbackFor = Exception.class)
	public void pause(String id, Long expectedRevision) {
		PromotionActivity activity = writable(id, expectedRevision);
		if (!PromotionActivity.STATUS_PUBLISHED.equals(activity.getStatus())
				|| !activity.getEndTime().isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("仅未结束的已发布活动可暂停");
		}
		activity.setStatus(PromotionActivity.STATUS_PAUSED);
		persist(activity, expectedRevision);
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(String id, Long expectedRevision) {
		PromotionActivity activity = writable(id, expectedRevision);
		if (PromotionActivity.STATUS_PUBLISHED.equals(activity.getStatus())
				&& activity.getEndTime() != null && activity.getEndTime().isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("未结束的已发布活动请先暂停后再删除");
		}
		int changed = mapper.update(null, Wrappers.lambdaUpdate(PromotionActivity.class)
			.eq(PromotionActivity::getId, id).eq(PromotionActivity::getTenantId, tenant())
			.eq(PromotionActivity::getRevision, expectedRevision)
			.set(PromotionActivity::getDelFlag, "1")
			.set(PromotionActivity::getRevision, expectedRevision + 1));
		if (changed != 1) { conflict(); }
	}

	private PromotionActivity writable(String id, Long expectedRevision) {
		PromotionActivity activity = mapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
			.eq(PromotionActivity::getId, id).eq(PromotionActivity::getTenantId, tenant()).last("FOR UPDATE"));
		if (activity == null || !tenant().equals(activity.getTenantId())) { throw new ArynBusinessException("活动不存在"); }
		B2bActivityAdmission.requireManual(activity);
		if (expectedRevision == null || expectedRevision < 0 || !expectedRevision.equals(activity.getRevision())) {
			conflict();
		}
		return activity;
	}

	private void persist(PromotionActivity activity, Long expectedRevision) {
		activity.setRevision(expectedRevision + 1);
		activity.setUpdateTime(LocalDateTime.now());
		PromotionActivity patch = new PromotionActivity();
		BeanUtils.copyProperties(activity, patch);
		patch.setTotalQuota(null);
		patch.setCalendarId(null);
		if (mapper.update(patch, Wrappers.lambdaUpdate(PromotionActivity.class)
			.eq(PromotionActivity::getId, activity.getId()).eq(PromotionActivity::getTenantId, tenant())
			.eq(PromotionActivity::getRevision, expectedRevision)
			.set(PromotionActivity::getTotalQuota, activity.getTotalQuota())
			.set(PromotionActivity::getCalendarId, activity.getCalendarId())) != 1) { conflict(); }
	}

	private PromotionActivity editable(B2bActivitySaveDTO dto) {
		if (dto == null) { throw new ArynBusinessException("活动不能为空"); }
		PromotionActivity activity = new PromotionActivity();
		BeanUtils.copyProperties(dto, activity);
		return activity;
	}

	private void validate(PromotionActivity activity) {
		B2bActivityAdmission.requireManual(activity);
		if (!StringUtils.hasText(activity.getActivityName())) { throw new ArynBusinessException("活动名称不能为空"); }
		if (activity.getStartTime() == null || activity.getEndTime() == null
				|| !activity.getStartTime().isBefore(activity.getEndTime())) {
			throw new ArynBusinessException("活动开始时间必须早于结束时间");
		}
		if (!List.of("1", "2", "4", "5", "6", "7", "8").contains(
			activity.getScopeType() == null ? "" : activity.getScopeType())) {
			throw new ArynBusinessException("非法的活动适用范围");
		}
		if (!"1".equals(activity.getScopeType()) && !StringUtils.hasText(activity.getScopeValue())) {
			throw new ArynBusinessException("指定适用范围不能为空");
		}
		if (StringUtils.hasText(activity.getPurchaseScene()) && !List.of("1", "2", "3").contains(activity.getPurchaseScene())) {
			throw new ArynBusinessException("非法的购买场景");
		}
		if (activity.getTotalBudget() == null) { activity.setTotalBudget(BigDecimal.ZERO); }
		validateLimit(activity.getTotalBudget(), activity.getConsumedBudget(), "活动预算");
		validateLimit(activity.getTotalQuota() == null ? null : BigDecimal.valueOf(activity.getTotalQuota()),
			activity.getConsumedQuota() == null ? null : BigDecimal.valueOf(activity.getConsumedQuota()), "活动限量");
		if (activity.getPerUserLimit() != null && activity.getPerUserLimit() < 0
				|| activity.getGiftStock() != null && activity.getGiftStock() < 0) {
			throw new ArynBusinessException("活动次数与赠品库存不得为负数");
		}
		if (!StringUtils.hasText(activity.getCouponStackable())) { activity.setCouponStackable("1"); }
		if (!StringUtils.hasText(activity.getPointsStackable())) { activity.setPointsStackable("1"); }
		if (!List.of("0", "1").contains(activity.getCouponStackable())
				|| !List.of("0", "1").contains(activity.getPointsStackable())) {
			throw new ArynBusinessException("非法的券或积分叠加策略");
		}
		if (StringUtils.hasText(activity.getCalendarId())) {
			PromoCalendar calendar = calendarMapper.selectOne(Wrappers.lambdaQuery(PromoCalendar.class)
				.eq(PromoCalendar::getId, activity.getCalendarId()).eq(PromoCalendar::getTenantId, tenant()));
			if (calendar == null || !tenant().equals(calendar.getTenantId())) {
				throw new ArynBusinessException("采购档期不存在");
			}
		}
		governance.validateRules(activity);
	}

	private void validateLimit(BigDecimal limit, BigDecimal consumed, String name) {
		if (limit != null && (limit.signum() < 0 || limit.signum() > 0 && consumed != null && limit.compareTo(consumed) < 0)) {
			throw new ArynBusinessException(name + "不得为负数或低于已消耗额度");
		}
	}

	private String tenant() {
		String tenant = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenant)) { throw new ArynBusinessException("缺少租户上下文"); }
		return tenant;
	}

	private void conflict() { throw new ArynBusinessException(409, "活动已变更，请刷新后重试"); }

	private void assertLadderPricesBelowSales(PromotionActivity activity) {
		if (!PromotionActivity.TYPE_LADDER_PRICE.equals(activity.getActivityType())) { return; }
		JSONArray ladders = JSONUtil.parseObj(activity.getRules()).getJSONArray("skuLadders");
		List<String> ids = ladders.stream().map(row -> ((JSONObject) row).getStr("skuId")).distinct().toList();
		Map<String, BigDecimal> sales;
		try {
			sales = remoteGoodsSkuService.getBySkuIds(ids).stream()
				.collect(Collectors.toMap(GoodsSku::getId, GoodsSku::getSalesPrice, (a, b) -> a));
		}
		catch (Exception ex) { throw new ArynBusinessException("商品服务不可用，无法校验阶梯价与售价关系，请稍后重试"); }
		assertLadderPricesBelowSales(activity, sales);
	}

	private void assertLadderPricesBelowSales(PromotionActivity activity, Map<String, BigDecimal> sales) {
		if (!PromotionActivity.TYPE_LADDER_PRICE.equals(activity.getActivityType())) { return; }
		JSONArray ladders = JSONUtil.parseObj(activity.getRules()).getJSONArray("skuLadders");
		for (Object row : ladders) {
			JSONObject sku = (JSONObject) row;
			BigDecimal price = sales.get(sku.getStr("skuId"));
			if (price == null) { throw new ArynBusinessException("阶梯价 SKU 不存在，无法发布"); }
			for (Object tier : sku.getJSONArray("ladders")) {
				if (((JSONObject) tier).getBigDecimal("unitPrice").compareTo(price) >= 0) {
					throw new ArynBusinessException("阶梯价档价不低于当前售价，发布后不会生效，请先调整");
				}
			}
		}
	}
}
