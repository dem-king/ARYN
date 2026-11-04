package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncResultVO;
import com.aryn.cloud.promotion.service.impl.MallZoneActivitySyncServiceImpl;
import com.aryn.cloud.promotion.service.impl.MallZoneSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 专区设置：会员/出清专区开关、默认折扣率、成本兜底与排序；
 * 以及系统活动同步状态查询（G19）与手动重试（G13）。
 *
 * <p>路由（cloud 模式首段 /promotion 由网关补齐，boot 模式由 context-path /boot 统一加前缀，
 * 后端不硬编码 /boot）：
 * <ul>
 *   <li>{@code GET/PUT /zone/setting/{zoneType}} 专区设置（保持原契约）；</li>
 *   <li>{@code GET /zone/sync-status} 查询两个专区的开关、系统活动、rules SKU 数与上次同步结果；</li>
 *   <li>{@code POST /zone/sync} 触发当前租户重算，返回最新同步状态。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/zone")
@Tag(description = "zone-setting", name = "专区设置")
public class MallZoneSettingController {

	/** 手动同步同租户冷却时间：避免前端重复点击/刷新放大全租户重算（G11/G19）。 */
	private static final long MANUAL_SYNC_COOLDOWN_MILLIS = 30_000L;

	private final MallZoneSettingService mallZoneSettingService;

	private final MallZoneActivitySyncServiceImpl mallZoneActivitySyncService;

	/** 各租户上次手动触发同步的时间（单实例限流；多实例部署下仅作软保护）。 */
	private final Map<String, Long> lastManualSyncAt = new ConcurrentHashMap<>();

	@Operation(summary = "读取专区设置（未配置返回默认开启）")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/setting/{zoneType}")
	public Result<MallZoneSetting> get(@PathVariable String zoneType) {
		return Result.success(mallZoneSettingService.getOrDefault(ArynTenantContextHolder.getTenantId(), zoneType));
	}

	@SysLog("保存专区设置")
	@Operation(summary = "保存专区设置")
	@SaCheckPermission("promotion:shipactivity:save")
	@PutMapping("/setting/{zoneType}")
	public Result<MallZoneSetting> save(@PathVariable String zoneType, @RequestBody MallZoneSetting setting) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		MallZoneSetting saved = mallZoneSettingService.save(tenantId, zoneType, setting);
		syncQuietly(tenantId);
		return Result.success(saved);
	}

	@Operation(summary = "查询专区同步状态（开关/系统活动/rules数/上次同步结果）")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/sync-status")
	public Result<MallZoneSyncResultVO> syncStatus() {
		return Result.success(mallZoneActivitySyncService.syncStatus(ArynTenantContextHolder.getTenantId()));
	}

	@SysLog("手动触发专区同步")
	@Operation(summary = "手动触发专区同步（同租户 30 秒冷却）")
	@SaCheckPermission("promotion:shipactivity:page")
	@PostMapping("/sync")
	public Result<MallZoneSyncResultVO> sync() {
		String tenantId = ArynTenantContextHolder.getTenantId();
		long now = System.currentTimeMillis();
		Long last = lastManualSyncAt.get(tenantId);
		if (last != null && now - last < MANUAL_SYNC_COOLDOWN_MILLIS) {
			MallZoneSyncResultVO status = mallZoneActivitySyncService.syncStatus(tenantId);
			status.setMessage("30 秒内已触发过同步，已返回最新同步状态");
			return Result.success(status);
		}
		lastManualSyncAt.put(tenantId, now);
		MallZoneSyncResultVO result = mallZoneActivitySyncService.syncTenant(tenantId);
		result.setCheckedAt(LocalDateTime.now());
		return Result.success(result);
	}

	private void syncQuietly(String tenantId) {
		try {
			MallZoneSyncResultVO result = mallZoneActivitySyncService.syncTenant(tenantId);
			if (Boolean.FALSE.equals(result.getSuccess())) {
				log.warn("专区设置保存后同步失败，等待每日兜底任务 tenant={} message={}", tenantId,
						result.getMessage());
			}
		}
		catch (Exception ex) {
			log.warn("专区设置保存后同步失败，等待每日兜底任务 tenant={}", tenantId, ex);
		}
	}

}
