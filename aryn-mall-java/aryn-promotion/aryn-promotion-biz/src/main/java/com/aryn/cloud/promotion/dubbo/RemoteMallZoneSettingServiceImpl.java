package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.aryn.cloud.promotion.service.impl.MallZoneSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 专区设置远程实现：商品域按显式租户读取开关/默认折扣/成本兜底/排序。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMallZoneSettingServiceImpl implements RemoteMallZoneSettingService {

	private final MallZoneSettingService mallZoneSettingService;

	@Override
	public MallZoneSettingSnapshotVO getSetting(String tenantId, String zoneType) {
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			if (StringUtils.hasText(tenantId)) {
				ArynTenantContextHolder.setTenantId(tenantId);
			}
			return mallZoneSettingService.getSnapshot(tenantId, zoneType);
		}
		catch (Exception ex) {
			log.warn("专区设置查询失败 tenant={} zone={}，按默认开启处理", tenantId, zoneType, ex);
			MallZoneSettingSnapshotVO fallback = new MallZoneSettingSnapshotVO();
			fallback.setZoneType(zoneType);
			fallback.setEnabled(true);
			fallback.setSortMode("default");
			return fallback;
		}
		finally {
			restoreTenant(snapshot);
		}
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
