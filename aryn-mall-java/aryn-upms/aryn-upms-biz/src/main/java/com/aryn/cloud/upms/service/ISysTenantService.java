
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.dto.RegisterTenantDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;

import java.time.LocalDateTime;

/**
 * 租户管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysTenantService extends IService<SysTenant> {

	boolean saveTenant(SysTenant sysTenant);

	/**
	 * 修改租户；停用、删除或授权到期时在事务提交后撤销该租户全部会话。
	 */
	boolean updateTenant(SysTenant sysTenant);

	/**
	 * 删除租户并撤销该租户全部会话；平台租户不可删除。
	 */
	boolean removeTenant(String id);

	/**
	 * 启用/停用租户；停用时在事务提交后撤销该租户全部会话。
	 */
	boolean changeStatus(String id, String status);

	/**
	 * 续期；从当前授权结束时间与当前时间的较晚者起顺延。
	 * @return 新的授权结束时间
	 */
	LocalDateTime renew(String id, int days);

	/**
	 * 强制下线该租户全部会话。
	 * @return 实际撤销的会话数
	 */
	int kickoutSessions(String id);

	boolean register(RegisterTenantDTO registerTenantDTO);

	/**
	 * 注册租户方法
	 * @param sysTenant
	 * @param type 注册类型： phone.手机号注册；username.用户名注册
	 */
	void registerSysTenant(SysTenant sysTenant, String type);

}
