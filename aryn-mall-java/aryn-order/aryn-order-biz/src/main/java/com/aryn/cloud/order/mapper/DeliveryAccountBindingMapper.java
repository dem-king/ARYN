
package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 配送员商城账号绑定
 *
 * @author aryn
 * @since 2026/9/5
 */
@Mapper
public interface DeliveryAccountBindingMapper extends BaseMapper<DeliveryAccountBinding> {

	/**
	 * 按商城用户加锁查询绑定行（含已解绑，用于重新绑定复用行）
	 *
	 * <p>FOR UPDATE 串行化同一商城用户的并发绑定；
	 * 租户条件由租户拦截器自动追加。
	 * @param mallUserId 商城用户ID
	 * @return 绑定行，从未绑定返回 null
	 */
	@Select("SELECT * FROM delivery_account_binding WHERE mall_user_id = #{mallUserId} AND del_flag = '0' FOR UPDATE")
	DeliveryAccountBinding selectByMallUserForUpdate(@Param("mallUserId") String mallUserId);

	/**
	 * 按员工账号加锁查询有效绑定行
	 *
	 * <p>FOR UPDATE 串行化同一员工账号的并发绑定；
	 * 租户条件由租户拦截器自动追加。
	 * @param sysUserId 员工账号ID
	 * @return 有效绑定行，未绑定返回 null
	 */
	@Select("SELECT * FROM delivery_account_binding WHERE sys_user_id = #{sysUserId} AND status = '1'"
			+ " AND del_flag = '0' FOR UPDATE")
	DeliveryAccountBinding selectActiveBySysUserForUpdate(@Param("sysUserId") String sysUserId);

}
