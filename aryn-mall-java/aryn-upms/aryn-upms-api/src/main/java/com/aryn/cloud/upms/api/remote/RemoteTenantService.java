
package com.aryn.cloud.upms.api.remote;

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

}
