package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;

/**
 * 订单归属销售解析（Dubbo）
 * <p>
 * 供 order 域下单时写入订单销售快照；只返回在职销售。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface RemoteSalesAttributionService {

	/**
	 * 解析会员当前应归属的销售。
	 * <p>判定顺序：已绑定销售 → 待转交认领中的销售 → 无。
	 * @param tenantId 租户ID
	 * @param userId 买家会员ID
	 * @return 归属销售（ID+姓名）；无归属返回 null
	 */
	SalesAttributionDTO resolve(String tenantId, String userId);

}
