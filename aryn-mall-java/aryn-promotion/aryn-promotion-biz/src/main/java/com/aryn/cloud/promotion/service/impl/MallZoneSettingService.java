package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.aryn.cloud.promotion.mapper.MallZoneSettingMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 会员/出清专区设置：开关、默认折扣、成本兜底与排序。
 *
 * <p>未配置时返回 enabled=true 的默认值，不落库；关闭开关后同步服务会清空对应
 * 系统托管活动 rules，C 端不再计算身份价。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallZoneSettingService {

	private static final Set<String> SORT_MODES = Set.of("default", "price_asc", "discount_desc");

	private static final Set<String> ZONE_TYPES = Set.of(MallZoneSetting.ZONE_MEMBER,
			MallZoneSetting.ZONE_CLEARANCE);

	private final MallZoneSettingMapper mallZoneSettingMapper;

	public MallZoneSetting getOrDefault(String tenantId, String zoneType) {
		String type = normalizeZoneType(zoneType);
		MallZoneSetting setting = mallZoneSettingMapper.selectOne(Wrappers.<MallZoneSetting>lambdaQuery()
			.eq(MallZoneSetting::getTenantId, tenantId)
			.eq(MallZoneSetting::getZoneType, type)
			.last("LIMIT 1"));
		if (setting == null) {
			setting = new MallZoneSetting();
			setting.setTenantId(tenantId);
			setting.setZoneType(type);
			setting.setEnabled("1");
			setting.setSortMode("default");
			setting.setDefaultDiscountRate(null);
		}
		return setting;
	}

	public MallZoneSettingSnapshotVO getSnapshot(String tenantId, String zoneType) {
		MallZoneSetting setting = getOrDefault(tenantId, zoneType);
		MallZoneSettingSnapshotVO vo = new MallZoneSettingSnapshotVO();
		vo.setZoneType(setting.getZoneType());
		vo.setEnabled(!"0".equals(setting.getEnabled()));
		vo.setDefaultDiscountRate(setting.getDefaultDiscountRate());
		vo.setCostFloorMultiplier(setting.getCostFloorMultiplier());
		vo.setSortMode(setting.getSortMode());
		return vo;
	}

	/**
	 * 记录系统活动同步结果（G19）：同步状态属于运维观测数据，写失败只告警，不影响打标主流程。
	 *
	 * @param tenantId 租户
	 * @param zoneType member / clearance
	 * @param status SUCCESS / FAILED
	 * @param error 失败原因（截断 500 字符）
	 * @param time 同步时间
	 */
	public void recordSyncStatus(String tenantId, String zoneType, String status, String error, LocalDateTime time) {
		String type = normalizeZoneType(zoneType);
		LocalDateTime syncTime = time == null ? LocalDateTime.now() : time;
		String message = truncate(error, 500);
		try {
			MallZoneSetting exists = mallZoneSettingMapper.selectOne(Wrappers.<MallZoneSetting>lambdaQuery()
				.eq(MallZoneSetting::getTenantId, tenantId)
				.eq(MallZoneSetting::getZoneType, type)
				.last("LIMIT 1"));
			if (exists == null) {
				MallZoneSetting setting = new MallZoneSetting();
				setting.setTenantId(tenantId);
				setting.setZoneType(type);
				setting.setEnabled("1");
				setting.setSortMode("default");
				setting.setLastSyncTime(syncTime);
				setting.setLastSyncStatus(status);
				setting.setLastSyncError(message);
				setting.setDelFlag("0");
				setting.setCreateTime(syncTime);
				setting.setUpdateTime(syncTime);
				mallZoneSettingMapper.insert(setting);
				return;
			}
			mallZoneSettingMapper.update(null, Wrappers.<MallZoneSetting>lambdaUpdate()
				.eq(MallZoneSetting::getId, exists.getId())
				.eq(MallZoneSetting::getTenantId, tenantId)
				.set(MallZoneSetting::getLastSyncTime, syncTime)
				.set(MallZoneSetting::getLastSyncStatus, status)
				.set(MallZoneSetting::getLastSyncError, message)
				.set(MallZoneSetting::getUpdateTime, syncTime));
		}
		catch (Exception ex) {
			log.warn("记录专区同步状态失败（不影响同步结果） tenant={} zone={} status={}", tenantId, type, status, ex);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public MallZoneSetting save(String tenantId, String zoneType, MallZoneSetting incoming) {
		String type = normalizeZoneType(zoneType);
		validate(incoming);
		if (!StringUtils.hasText(incoming.getEnabled())) {
			incoming.setEnabled("1");
		}
		if (!StringUtils.hasText(incoming.getSortMode())) {
			incoming.setSortMode("default");
		}
		MallZoneSetting exists = mallZoneSettingMapper.selectOne(Wrappers.<MallZoneSetting>lambdaQuery()
			.eq(MallZoneSetting::getTenantId, tenantId)
			.eq(MallZoneSetting::getZoneType, type)
			.last("LIMIT 1"));
		if (exists == null) {
			incoming.setTenantId(tenantId);
			incoming.setZoneType(type);
			incoming.setDelFlag("0");
			incoming.setCreateTime(LocalDateTime.now());
			mallZoneSettingMapper.insert(incoming);
			return incoming;
		}
		// 显式 set：运营清空默认折扣/成本兜底倍率时也要落 null（updateById 的 NOT_NULL 策略会跳过 null）
		LocalDateTime now = LocalDateTime.now();
		mallZoneSettingMapper.update(null, Wrappers.<MallZoneSetting>lambdaUpdate()
			.eq(MallZoneSetting::getId, exists.getId())
			.eq(MallZoneSetting::getTenantId, tenantId)
			.set(MallZoneSetting::getEnabled, incoming.getEnabled())
			.set(MallZoneSetting::getDefaultDiscountRate, incoming.getDefaultDiscountRate())
			.set(MallZoneSetting::getCostFloorMultiplier, incoming.getCostFloorMultiplier())
			.set(MallZoneSetting::getSortMode, incoming.getSortMode())
			.set(MallZoneSetting::getUpdateTime, now));
		incoming.setId(exists.getId());
		incoming.setTenantId(tenantId);
		incoming.setZoneType(type);
		incoming.setUpdateTime(now);
		return incoming;
	}

	private void validate(MallZoneSetting setting) {
		if (setting == null) {
			throw new ArynBusinessException("专区设置不能为空");
		}
		if (StringUtils.hasText(setting.getEnabled()) && !"0".equals(setting.getEnabled())
				&& !"1".equals(setting.getEnabled())) {
			throw new ArynBusinessException("专区开关只能为 0 或 1");
		}
		BigDecimal rate = setting.getDefaultDiscountRate();
		if (rate != null && (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0)) {
			throw new ArynBusinessException("默认折扣率必须在 (0, 1) 区间内，如 0.9 表示 9 折");
		}
		BigDecimal costFloor = setting.getCostFloorMultiplier();
		if (costFloor != null && (costFloor.signum() <= 0 || costFloor.compareTo(BigDecimal.TEN) > 0)) {
			throw new ArynBusinessException("成本兜底倍率必须在 (0, 10] 区间内，如 1.05 表示成本上浮 5%");
		}
		if (StringUtils.hasText(setting.getSortMode()) && !SORT_MODES.contains(setting.getSortMode())) {
			throw new ArynBusinessException("不支持的专区排序：" + setting.getSortMode());
		}
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private String normalizeZoneType(String zoneType) {
		if (!StringUtils.hasText(zoneType) || !ZONE_TYPES.contains(zoneType)) {
			throw new ArynBusinessException("专区类型只支持 member / clearance");
		}
		return zoneType;
	}

}
