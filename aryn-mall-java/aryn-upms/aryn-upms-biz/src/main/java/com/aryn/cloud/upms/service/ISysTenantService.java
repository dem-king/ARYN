
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.dto.RegisterTenantDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;

/**
 * 租户管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysTenantService extends IService<SysTenant> {

	boolean saveTenant(SysTenant sysTenant);

	boolean register(RegisterTenantDTO registerTenantDTO);

	/**
	 * 注册租户方法
	 * @param sysTenant
	 * @param type 注册类型： phone.手机号注册；username.用户名注册
	 */
	void registerSysTenant(SysTenant sysTenant, String type);

}
