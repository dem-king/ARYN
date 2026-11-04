package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;
import com.aryn.cloud.product.api.remote.RemoteGoodsZoneTagService;
import com.aryn.cloud.promotion.api.entity.ClearanceRule;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSyncService;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncResultVO;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncStateVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会员/出清专区系统托管活动同步。
 *
 * <p>商品标签是唯一事实来源：每次同步按标签全量重算 rules，系统活动按租户唯一且 id 稳定
 * （不删除重建，避免在途锁/快照错配）。会员活动复用 type=10（最低等级门槛 + SKU 固定价），
 * 出清活动使用 type=11；两者同优先级，同 SKU 由引擎取低。
 *
 * <p>幂等：重复执行不产生第二条系统活动，rules 按 skuId 全量覆盖；到期出清价在此移除，
 * 引擎侧仍会在计算时二次校验 expireTime，避免同步延迟导致过期价成交。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class MallZoneActivitySyncServiceImpl implements RemoteMallZoneSyncService {

	public static final String SYSTEM_MEMBER_ZONE_CODE = "SYS_MEMBER_ZONE";

	public static final String SYSTEM_CLEARANCE_ZONE_CODE = "SYS_CLEARANCE_ZONE";

	private static final String MEMBER_ACTIVITY_NAME = "会员专区（系统托管）";

	private static final String CLEARANCE_ACTIVITY_NAME = "低价捡漏专区（系统托管）";

	/** 系统活动优先级最低：会员价已统一由商品标签自动同步，历史手工会员价活动已下线。 */
	private static final int SYSTEM_PRIORITY = -100;

	private static final BigDecimal DEFAULT_MEMBER_FLOOR = new BigDecimal("0.01");

	private static final LocalDateTime SYSTEM_START = LocalDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.MIN);

	private static final LocalDateTime SYSTEM_END = LocalDateTime.of(LocalDate.of(2099, 12, 31), LocalTime.of(23, 59, 59));

	/** 系统托管活动标识判断（SYS_ 前缀）。 */
	public static boolean isSystemActivity(PromotionActivity activity) {
		return activity != null && StringUtils.hasText(activity.getActivityCode())
				&& activity.getActivityCode().startsWith("SYS_");
	}

	private final PromotionActivityMapper promotionActivityMapper;

	private final MallZoneSettingService mallZoneSettingService;

	private final ClearanceRuleService clearanceRuleService;

	@DubboReference
	private RemoteGoodsZoneTagService remoteGoodsZoneTagService;

	@DubboReference
	private RemoteMemberLevelService remoteMemberLevelService;

	@DubboReference
	private RemoteTenantService remoteTenantService;

	@Override
	public MallZoneSyncResultVO syncTenant(String tenantId) {
		LocalDateTime startTime = LocalDateTime.now();
		List<MallZoneSyncStateVO> zones = new ArrayList<>();
		if (!StringUtils.hasText(tenantId)) {
			return MallZoneSyncResultVO.failed("租户ID为空，无法同步专区活动", startTime, zones);
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			List<GoodsZoneSkuSnapshotDTO> skus;
			try {
				skus = remoteGoodsZoneTagService.listZoneSkus(tenantId);
			}
			catch (Exception ex) {
				// G13：快照查询失败必须中止同步，禁止用空快照覆盖已有系统活动 rules（会清空全租户专区价）
				log.error("专区商品快照查询失败，中止本次同步 tenant={}", tenantId, ex);
				String message = "专区商品快照查询失败：" + rootMessage(ex);
				zones.add(failedState(MallZoneSetting.ZONE_MEMBER, message, startTime));
				zones.add(failedState(MallZoneSetting.ZONE_CLEARANCE, message, startTime));
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_MEMBER,
						MallZoneSyncStateVO.LAST_SYNC_FAILED, message, startTime);
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_CLEARANCE,
						MallZoneSyncStateVO.LAST_SYNC_FAILED, message, startTime);
				return MallZoneSyncResultVO.failed(message, startTime, zones);
			}
			LocalDateTime now = LocalDateTime.now();
			MallZoneSetting memberSetting = mallZoneSettingService.getOrDefault(tenantId,
					MallZoneSetting.ZONE_MEMBER);
			MallZoneSetting clearanceSetting = mallZoneSettingService.getOrDefault(tenantId,
					MallZoneSetting.ZONE_CLEARANCE);
			boolean failed = false;
			StringBuilder messages = new StringBuilder();
			try {
				zones.add(syncMemberZone(tenantId, skus, now, memberSetting));
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_MEMBER,
						MallZoneSyncStateVO.LAST_SYNC_SUCCESS, null, now);
			}
			catch (Exception ex) {
				failed = true;
				String message = "会员专区同步失败：" + rootMessage(ex);
				appendMessage(messages, message);
				log.error("会员专区系统活动同步失败 tenant={}", tenantId, ex);
				zones.add(failedState(MallZoneSetting.ZONE_MEMBER, message, now));
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_MEMBER,
						MallZoneSyncStateVO.LAST_SYNC_FAILED, message, now);
			}
			try {
				zones.add(syncClearanceZone(tenantId, skus, now, clearanceSetting,
						clearanceRuleService.listEnabled(tenantId)));
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_CLEARANCE,
						MallZoneSyncStateVO.LAST_SYNC_SUCCESS, null, now);
			}
			catch (Exception ex) {
				failed = true;
				String message = "出清专区同步失败：" + rootMessage(ex);
				appendMessage(messages, message);
				log.error("出清专区系统活动同步失败 tenant={}", tenantId, ex);
				zones.add(failedState(MallZoneSetting.ZONE_CLEARANCE, message, now));
				recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_CLEARANCE,
						MallZoneSyncStateVO.LAST_SYNC_FAILED, message, now);
			}
			if (failed) {
				return MallZoneSyncResultVO.failed(messages.toString(), LocalDateTime.now(), zones);
			}
			return MallZoneSyncResultVO.success("专区活动同步成功", LocalDateTime.now(), zones);
		}
		catch (Exception ex) {
			// 兜底：任何未预期异常（如专区设置读取失败）都不得抛给调用方/清空活动，转为 FAILED 结果
			log.error("专区系统活动同步异常 tenant={}", tenantId, ex);
			String message = "专区活动同步异常：" + rootMessage(ex);
			recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_MEMBER,
					MallZoneSyncStateVO.LAST_SYNC_FAILED, message, startTime);
			recordSyncStatusQuietly(tenantId, MallZoneSetting.ZONE_CLEARANCE,
					MallZoneSyncStateVO.LAST_SYNC_FAILED, message, startTime);
			zones.add(failedState(MallZoneSetting.ZONE_MEMBER, message, startTime));
			zones.add(failedState(MallZoneSetting.ZONE_CLEARANCE, message, startTime));
			return MallZoneSyncResultVO.failed(message, startTime, zones);
		}
		finally {
			restoreTenant(snapshot);
		}
	}

	/**
	 * 读取当前租户两个专区的同步状态（开关、系统活动、rules SKU 数、上次同步结果）。
	 *
	 * <p>供管理端 {@code GET /zone/sync-status} 与手动重试 {@code POST /zone/sync} 复用。
	 */
	public MallZoneSyncResultVO syncStatus(String tenantId) {
		LocalDateTime checkedAt = LocalDateTime.now();
		List<MallZoneSyncStateVO> zones = new ArrayList<>();
		if (!StringUtils.hasText(tenantId)) {
			return MallZoneSyncResultVO.failed("租户ID为空，无法查询同步状态", null, zones);
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		boolean failed = false;
		StringBuilder messages = new StringBuilder();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			for (String zoneType : List.of(MallZoneSetting.ZONE_MEMBER, MallZoneSetting.ZONE_CLEARANCE)) {
				try {
					MallZoneSyncStateVO state = buildZoneState(tenantId, zoneType);
					zones.add(state);
					if (MallZoneSyncStateVO.LAST_SYNC_FAILED.equals(state.getLastSyncStatus())) {
						failed = true;
						appendMessage(messages, zoneType + "专区上次同步失败：" + state.getLastError());
					}
				}
				catch (Exception ex) {
					failed = true;
					String message = zoneType + "专区状态查询失败：" + rootMessage(ex);
					appendMessage(messages, message);
					log.warn("专区同步状态查询失败 tenant={} zone={}", tenantId, zoneType, ex);
					zones.add(failedState(zoneType, message, null));
				}
			}
		}
		finally {
			restoreTenant(snapshot);
		}
		MallZoneSyncResultVO result = failed
				? MallZoneSyncResultVO.failed(messages.toString(), null, zones)
				: MallZoneSyncResultVO.success(null, null, zones);
		result.setCheckedAt(checkedAt);
		return result;
	}

	/**
	 * 全租户兜底同步：每日 Job 处理到期退出与打标后同步失败的漏单。
	 */
	public void syncAllTenants() {
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		for (SysTenant tenant : tenants) {
			if (tenant == null || !StringUtils.hasText(tenant.getId())) {
				continue;
			}
			try {
				syncTenant(tenant.getId());
			}
			catch (Exception ex) {
				log.error("租户专区活动兜底同步失败 tenant={}", tenant.getId(), ex);
			}
		}
	}

	private MallZoneSyncStateVO syncMemberZone(String tenantId, List<GoodsZoneSkuSnapshotDTO> skus,
			LocalDateTime now, MallZoneSetting setting) {
		List<MemberLevelSnapshotVO> levels = remoteMemberLevelService.listLevels(tenantId);
		String minLevelId = levels.stream()
			.filter(Objects::nonNull)
			.map(MemberLevelSnapshotVO::getLevelId)
			.filter(StringUtils::hasText)
			.findFirst()
			.orElse(null);
		List<JSONObject> items = new ArrayList<>();
		boolean zoneEnabled = !"0".equals(setting.getEnabled());
		if (zoneEnabled && StringUtils.hasText(minLevelId)) {
			for (GoodsZoneSkuSnapshotDTO sku : skus) {
				if (!Boolean.TRUE.equals(sku.getMemberItem()) || sku.getSalesPrice() == null
						|| sku.getSalesPrice().signum() <= 0) {
					continue;
				}
				BigDecimal price = resolveMemberPrice(sku, setting.getDefaultDiscountRate(),
						setting.getCostFloorMultiplier());
				// 倒挂/无规则：不落价（空表示用等级折扣率，由订单侧取优）
				if (price == null || price.compareTo(sku.getSalesPrice()) >= 0) {
					continue;
				}
				JSONObject item = new JSONObject();
				item.set("skuId", sku.getSkuId());
				item.set("unitPrice", price);
				items.add(item);
			}
		}
		JSONObject rules = new JSONObject();
		rules.set("minLevelId", minLevelId);
		rules.set("skus", new JSONArray(items));
		PromotionActivity activity = upsertSystemActivity(tenantId, SYSTEM_MEMBER_ZONE_CODE,
				MEMBER_ACTIVITY_NAME, PromotionActivity.TYPE_MEMBER_PRICE, rules, now);
		return successState(MallZoneSetting.ZONE_MEMBER, setting, activity, items.size(), now);
	}

	private MallZoneSyncStateVO syncClearanceZone(String tenantId, List<GoodsZoneSkuSnapshotDTO> skus,
			LocalDateTime now, MallZoneSetting setting, List<ClearanceRule> clearanceRules) {
		List<JSONObject> items = new ArrayList<>();
		boolean zoneEnabled = !"0".equals(setting.getEnabled());
		for (GoodsZoneSkuSnapshotDTO sku : skus) {
			if (!zoneEnabled || !Boolean.TRUE.equals(sku.getClearanceItem()) || sku.getSalesPrice() == null
					|| sku.getSalesPrice().signum() <= 0) {
				continue;
			}
			BigDecimal price = resolveClearancePrice(sku, setting.getDefaultDiscountRate(),
					clearanceRules, now);
			if (price == null || price.signum() <= 0 || price.compareTo(sku.getSalesPrice()) >= 0) {
				if (price != null && price.compareTo(sku.getSalesPrice()) >= 0) {
					log.warn("出清价不低于当前售价，跳过 sku={} price={} salesPrice={}", sku.getSkuId(), price,
							sku.getSalesPrice());
				}
				continue;
			}
			if (sku.getClearanceExpireTime() != null && !sku.getClearanceExpireTime().isAfter(now)) {
				continue;
			}
			JSONObject item = new JSONObject();
			item.set("skuId", sku.getSkuId());
			item.set("price", price);
			if (sku.getClearanceExpireTime() != null) {
				item.set("expireTime", sku.getClearanceExpireTime().toString());
			}
			items.add(item);
		}
		JSONObject rules = new JSONObject();
		rules.set("skus", new JSONArray(items));
		PromotionActivity activity = upsertSystemActivity(tenantId, SYSTEM_CLEARANCE_ZONE_CODE,
				CLEARANCE_ACTIVITY_NAME, PromotionActivity.TYPE_CLEARANCE, rules, now);
		return successState(MallZoneSetting.ZONE_CLEARANCE, setting, activity, items.size(), now);
	}

	/**
	 * 会员价：商品显式下限优先；未配置时可按成本 × 倍率自动兜底；都没有则 0.01。
	 */
	private BigDecimal resolveMemberPrice(GoodsZoneSkuSnapshotDTO sku, BigDecimal defaultRate,
			BigDecimal costFloorMultiplier) {
		BigDecimal rate = sku.getMemberDiscountRate();
		if ((rate == null || rate.signum() <= 0) && defaultRate != null && defaultRate.signum() > 0) {
			rate = defaultRate;
		}
		BigDecimal ratePrice = null;
		if (rate != null && rate.signum() > 0) {
			ratePrice = sku.getSalesPrice().multiply(rate).setScale(2, RoundingMode.HALF_UP);
		}
		BigDecimal price = ratePrice;
		if (sku.getMemberPriceFixed() != null && sku.getMemberPriceFixed().signum() > 0) {
			price = price == null ? sku.getMemberPriceFixed() : price.min(sku.getMemberPriceFixed());
		}
		if (price == null) {
			return null;
		}
		BigDecimal floor = sku.getMemberPriceFloor() != null && sku.getMemberPriceFloor().signum() > 0
				? sku.getMemberPriceFloor() : null;
		if (floor == null && costFloorMultiplier != null && costFloorMultiplier.signum() > 0
				&& sku.getCostPrice() != null && sku.getCostPrice().signum() > 0) {
			// 未显式配置下限：按成本兜底，避免批量打标时不同成本的商品共用一个手填值
			floor = sku.getCostPrice().multiply(costFloorMultiplier).setScale(2, RoundingMode.HALF_UP);
		}
		if (floor == null || floor.signum() <= 0) {
			floor = DEFAULT_MEMBER_FLOOR;
		}
		return price.max(floor).setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal resolveClearancePrice(GoodsZoneSkuSnapshotDTO sku, BigDecimal defaultRate,
			List<ClearanceRule> clearanceRules, LocalDateTime now) {
		BigDecimal price = null;
		if (sku.getClearancePrice() != null && sku.getClearancePrice().signum() > 0) {
			price = sku.getClearancePrice().setScale(2, RoundingMode.HALF_UP);
		}
		else {
			BigDecimal rate = sku.getClearanceDiscountRate();
			if ((rate == null || rate.signum() <= 0) && defaultRate != null && defaultRate.signum() > 0) {
				rate = defaultRate;
			}
			if (rate != null && rate.signum() > 0 && sku.getOriginalPrice() != null
					&& sku.getOriginalPrice().signum() > 0) {
				price = sku.getOriginalPrice().multiply(rate).setScale(2, RoundingMode.HALF_UP);
			}
		}
		if (price == null) {
			return null;
		}
		// 时间分层：只应用最深一档（最小折扣率），不连乘；算价在同步任务完成
		BigDecimal tierRate = clearanceRuleService.resolveTierRate(clearanceRules,
				sku.getClearanceExpireTime(), now);
		if (tierRate != null) {
			price = price.multiply(tierRate).setScale(2, RoundingMode.HALF_UP);
		}
		return price;
	}

	private PromotionActivity upsertSystemActivity(String tenantId, String activityCode, String name, String type,
			JSONObject rules, LocalDateTime now) {
		JSONArray items = rules.getJSONArray("skus");
		boolean hasItems = items != null && !items.isEmpty();
		PromotionActivity activity = promotionActivityMapper.selectOne(Wrappers.<PromotionActivity>lambdaQuery()
			.eq(PromotionActivity::getTenantId, tenantId)
			.eq(PromotionActivity::getActivityCode, activityCode)
			.last("LIMIT 1"));
		if (activity == null) {
			// 无选品时不创建空系统活动：只有实际使用专区的租户才落记录
			if (!hasItems) {
				return null;
			}
			activity = new PromotionActivity();
			activity.setActivityCode(activityCode);
			activity.setTenantId(tenantId);
			activity.setActivityType(type);
			activity.setActivityName(name);
			activity.setScopeType("1");
			activity.setPriority(SYSTEM_PRIORITY);
			activity.setStartTime(SYSTEM_START);
			activity.setEndTime(SYSTEM_END);
			activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
			activity.setCouponStackable("1");
			activity.setPointsStackable("1");
			activity.setRules(JSONUtil.toJsonStr(rules));
			activity.setRemark("系统托管活动：由商品专区标签自动同步，请勿手工编辑");
			activity.setDelFlag("0");
			activity.setCreateTime(now);
			activity.setUpdateTime(now);
			try {
				promotionActivityMapper.insert(activity);
				log.info("创建专区系统活动 tenant={} code={} id={}", tenantId, activityCode, activity.getId());
				return activity;
			}
			catch (org.springframework.dao.DuplicateKeyException ex) {
				// 并发同步（打标保存 + 兜底 Job）撞唯一索引：回读后走更新，保证 activityId 稳定
				PromotionActivity concurrent = promotionActivityMapper.selectOne(
						Wrappers.<PromotionActivity>lambdaQuery()
							.eq(PromotionActivity::getTenantId, tenantId)
							.eq(PromotionActivity::getActivityCode, activityCode)
							.last("LIMIT 1"));
				if (concurrent == null) {
					throw ex;
				}
				activity = concurrent;
			}
		}
		// id 必须稳定：只更新 rules 与运行态，不删除重建（在途锁/快照依赖 activityId）
		activity.setRules(JSONUtil.toJsonStr(rules));
		activity.setActivityType(type);
		activity.setActivityName(name);
		activity.setScopeType("1");
		activity.setPriority(SYSTEM_PRIORITY);
		activity.setStartTime(SYSTEM_START);
		activity.setEndTime(SYSTEM_END);
		// 无选品时暂停：避免 /app/member/prices 反复命中空系统活动产生告警，
		// 会员专区货盘仍会回落为标签选品
		activity.setStatus(hasItems ? PromotionActivity.STATUS_PUBLISHED : PromotionActivity.STATUS_PAUSED);
		activity.setDelFlag("0");
		activity.setUpdateTime(now);
		promotionActivityMapper.updateById(activity);
		return activity;
	}

	/**
	 * 组装单个专区的同步状态快照（活动 + 设置）。
	 */
	private MallZoneSyncStateVO buildZoneState(String tenantId, String zoneType) {
		MallZoneSyncStateVO state = new MallZoneSyncStateVO();
		state.setZone(zoneType);
		MallZoneSetting setting = mallZoneSettingService.getOrDefault(tenantId, zoneType);
		state.setEnabled(!"0".equals(setting.getEnabled()));
		state.setLastSyncTime(setting.getLastSyncTime());
		state.setLastSyncStatus(StringUtils.hasText(setting.getLastSyncStatus())
				? setting.getLastSyncStatus() : MallZoneSyncStateVO.LAST_SYNC_UNKNOWN);
		state.setLastError(setting.getLastSyncError());
		String activityCode = MallZoneSetting.ZONE_MEMBER.equals(zoneType)
				? SYSTEM_MEMBER_ZONE_CODE : SYSTEM_CLEARANCE_ZONE_CODE;
		PromotionActivity activity = promotionActivityMapper.selectOne(Wrappers.<PromotionActivity>lambdaQuery()
			.eq(PromotionActivity::getTenantId, tenantId)
			.eq(PromotionActivity::getActivityCode, activityCode)
			.last("LIMIT 1"));
		if (activity != null) {
			state.setActivityId(activity.getId());
			state.setActivityStatus(activity.getStatus());
			state.setRulesSkuCount(countRulesSkus(activity.getRules()));
		}
		else {
			state.setRulesSkuCount(0);
		}
		return state;
	}

	private MallZoneSyncStateVO successState(String zoneType, MallZoneSetting setting, PromotionActivity activity,
			int rulesSkuCount, LocalDateTime now) {
		MallZoneSyncStateVO state = new MallZoneSyncStateVO();
		state.setZone(zoneType);
		state.setEnabled(setting == null || !"0".equals(setting.getEnabled()));
		state.setActivityId(activity == null ? null : activity.getId());
		state.setActivityStatus(activity == null ? null : activity.getStatus());
		state.setRulesSkuCount(rulesSkuCount);
		state.setLastSyncTime(now);
		state.setLastSyncStatus(MallZoneSyncStateVO.LAST_SYNC_SUCCESS);
		return state;
	}

	private MallZoneSyncStateVO failedState(String zoneType, String message, LocalDateTime time) {
		MallZoneSyncStateVO state = new MallZoneSyncStateVO();
		state.setZone(zoneType);
		state.setLastSyncTime(time);
		state.setLastSyncStatus(MallZoneSyncStateVO.LAST_SYNC_FAILED);
		state.setLastError(message);
		return state;
	}

	/**
	 * 统计系统活动 rules 内的 SKU 数；rules 非法时按 0 处理（不阻断状态查询）。
	 */
	private int countRulesSkus(String rules) {
		if (!StringUtils.hasText(rules)) {
			return 0;
		}
		try {
			JSONArray skus = JSONUtil.parseObj(rules).getJSONArray("skus");
			return skus == null ? 0 : skus.size();
		}
		catch (Exception ex) {
			log.warn("解析专区系统活动 rules 失败，按 0 个 SKU 处理", ex);
			return 0;
		}
	}

	private void recordSyncStatusQuietly(String tenantId, String zoneType, String status, String error,
			LocalDateTime time) {
		try {
			mallZoneSettingService.recordSyncStatus(tenantId, zoneType, status, error, time);
		}
		catch (Exception ex) {
			log.warn("记录专区同步状态失败 tenant={} zone={} status={}", tenantId, zoneType, status, ex);
		}
	}

	private void appendMessage(StringBuilder messages, String message) {
		if (messages.length() > 0) {
			messages.append("；");
		}
		messages.append(message);
	}

	private String rootMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage();
		return StringUtils.hasText(message) ? message : root.getClass().getSimpleName();
	}

	private void restoreTenant(String snapshot) {
		if (snapshot == null) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(snapshot);
		}
	}

}
