package com.aryn.cloud.upms.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderMetricService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.upms.api.entity.SysLoginLog;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.entity.SysTenantPackage;
import com.aryn.cloud.upms.api.entity.SysUserOnline;
import com.aryn.cloud.upms.api.vo.TenantOverviewVO;
import com.aryn.cloud.upms.support.TenantOnlineSessionReader;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 平台侧租户概览统计。
 *
 * <p>本模块可直接统计账号、素材与登录信息；会员、商品、订单分属其它业务域，
 * 只能走 Dubbo。单个远程统计失败时对应字段降级为 null，不阻断整个概览。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOverviewService {

	private final ISysTenantService sysTenantService;

	private final ISysTenantPackageService sysTenantPackageService;

	private final ISysUserService sysUserService;

	private final ISysMaterialService sysMaterialService;

	private final ISysLoginLogService sysLoginLogService;

	private final TenantOnlineSessionReader onlineSessionReader;

	@DubboReference(check = false)
	private final RemoteMallUserService remoteMallUserService;

	@DubboReference(check = false)
	private final RemoteGoodsSpuService remoteGoodsSpuService;

	@DubboReference(check = false)
	private final RemoteOrderMetricService remoteOrderMetricService;

	public TenantOverviewVO getOverview(String tenantId) {
		SysTenant tenant = sysTenantService.getById(tenantId);
		if (tenant == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
		TenantOverviewVO overview = new TenantOverviewVO();
		overview.setId(tenant.getId());
		overview.setName(tenant.getName());
		overview.setPackageId(tenant.getPackageId());
		overview.setStatus(tenant.getStatus());
		overview.setBusinessMode(tenant.getBusinessMode());
		overview.setAuthBeginTime(tenant.getAuthBeginTime());
		overview.setAuthEndTime(tenant.getAuthEndTime());
		if (tenant.getAuthEndTime() != null) {
			overview.setRemainingDays(
					ChronoUnit.DAYS.between(LocalDate.now(), tenant.getAuthEndTime().toLocalDate()));
		}
		if (StrUtil.isNotBlank(tenant.getPackageId())) {
			SysTenantPackage tenantPackage = sysTenantPackageService.getById(tenant.getPackageId());
			overview.setPackageName(tenantPackage == null ? null : tenantPackage.getName());
		}
		overview.setOnlineSessionCount(countActiveSessions(tenantId));

		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			overview.setAdminUserCount(sysUserService.count());
			overview.setMaterialCount(sysMaterialService.count());
			overview.setStorageBytes(sumStorage());
			overview.setLastLoginTime(lastLoginTime());
			overview.setMallUserCount(safely("mallUser", tenantId, remoteMallUserService::countMallUsers));
			overview.setGoodsCount(safely("goods", tenantId, remoteGoodsSpuService::countGoods));
			overview.setOrderCount(safely("order", tenantId, remoteOrderMetricService::countOrders));
		}
		finally {
			restoreTenant(previousTenantId);
		}
		return overview;
	}

	private Long countActiveSessions(String tenantId) {
		List<SysUserOnline> sessions = onlineSessionReader.listByTenant(tenantId);
		return sessions.stream().filter(this::isSessionActive).count();
	}

	private boolean isSessionActive(SysUserOnline online) {
		if (StrUtil.isBlank(online.getTokenId())) {
			return false;
		}
		try {
			return StpUtil.getLoginIdByToken(online.getTokenId()) != null;
		}
		catch (Exception e) {
			return false;
		}
	}

	private Long sumStorage() {
		List<SysMaterial> materials = sysMaterialService
			.list(Wrappers.<SysMaterial>lambdaQuery().select(SysMaterial::getFileSize));
		return materials.stream()
			.map(SysMaterial::getFileSize)
			.filter(Objects::nonNull)
			.mapToLong(Long::longValue)
			.sum();
	}

	private LocalDateTime lastLoginTime() {
		SysLoginLog latest = sysLoginLogService.getOne(Wrappers.<SysLoginLog>lambdaQuery()
			.select(SysLoginLog::getCreateTime)
			.eq(SysLoginLog::getStatus, CommonConstants.LOGIN_LOG_STATUS_1)
			.orderByDesc(SysLoginLog::getCreateTime)
			.last("LIMIT 1"));
		return latest == null ? null : latest.getCreateTime();
	}

	private <T> T safely(String metric, String tenantId, Supplier<T> supplier) {
		try {
			return supplier.get();
		}
		catch (Exception e) {
			log.warn("租户概览统计失败 metric={} tenantId={}", metric, tenantId, e);
			return null;
		}
	}

	private void restoreTenant(String previousTenantId) {
		if (StrUtil.isBlank(previousTenantId)) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

}
