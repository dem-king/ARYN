package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;

/**
 * 销售员查询（Dubbo）
 * <p>
 * 供 user 域生成推广小程序码时校验会员是否为在职销售。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface RemoteSalesStaffService {

	/**
	 * 按销售本人的小程序会员ID查询在职销售。
	 * @param tenantId 租户ID
	 * @param memberUserId 销售会员ID
	 * @return 在职销售（ID+姓名）；不是销售或已离职返回 null
	 */
	SalesAttributionDTO getActiveStaffByMemberUserId(String tenantId, String memberUserId);

}
