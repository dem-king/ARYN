
package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.dto.TenantAccessDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;

import java.util.List;

/**
 * 租户
 *
 * @author 雨滴kian
 * @date 2023/1/06
 */
public interface RemoteTenantService {

	/**
	 * 查询全部有效租户
	 * @return
	 */
	List<SysTenant> list();

	/**
	 * 查询租户可用性快照；租户不存在或已逻辑删除时返回 null。
	 * @param tenantId 租户 ID
	 * @return 状态与授权有效期
	 */
	TenantAccessDTO getAccess(String tenantId);

}
