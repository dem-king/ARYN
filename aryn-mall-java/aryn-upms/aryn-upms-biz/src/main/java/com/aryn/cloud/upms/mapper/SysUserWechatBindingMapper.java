package com.aryn.cloud.upms.mapper;

import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 员工配送微信绑定持久层。 */
@Mapper
public interface SysUserWechatBindingMapper extends BaseMapper<SysUserWechatBinding> {

	@Insert("""
		INSERT INTO sys_user_wechat_binding
		(id, user_id, app_id, openid, status, bound_at, tenant_id, create_by, create_time, del_flag)
		VALUES
		(#{id}, #{userId}, #{appId}, #{openid}, #{status}, #{boundAt}, #{tenantId}, #{createBy},
		 #{createTime}, #{delFlag})
		ON DUPLICATE KEY UPDATE
		openid = IF(user_id = VALUES(user_id), VALUES(openid), NULL),
		status = 'BOUND', bound_at = VALUES(bound_at), unbound_at = NULL,
		update_by = VALUES(create_by), update_time = VALUES(create_time), del_flag = '0'
		""")
	int upsertBinding(SysUserWechatBinding binding);

	@Select("""
		SELECT openid FROM sys_user_wechat_binding
		WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND app_id = #{appId}
		  AND status = 'BOUND' AND del_flag = '0'
		LIMIT 1
		""")
	String selectBoundOpenId(@Param("tenantId") String tenantId, @Param("userId") String userId,
			@Param("appId") String appId);
}
