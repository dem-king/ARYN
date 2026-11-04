package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;

/**
 * 专区设置远程接口：商品域计算专区展示价/默认折扣时读取。
 *
 * @author aryn
 * @since 2026/11/03
 */
public interface RemoteMallZoneSettingService {

	/**
	 * 读取指定租户的专区设置；未配置时返回 enabled=true 的默认快照（不落库）。
	 *
	 * @param tenantId 租户 ID
	 * @param zoneType member / clearance
	 * @return 设置快照（非空）
	 */
	MallZoneSettingSnapshotVO getSetting(String tenantId, String zoneType);

}
