
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户关联角色
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

	/**
	 * 查询用户与角色的关联行（含已逻辑删除，用于再次授予时优先恢复）
	 *
	 * <p>自定义 SQL 不经过 @TableLogic 自动过滤；
	 * 租户条件由租户拦截器自动追加。
	 * @param userId 用户ID
	 * @param roleId 角色ID
	 * @return 关联行（有效行优先），从未关联返回 null
	 */
	@Select("SELECT * FROM sys_user_role WHERE user_id = #{userId} AND role_id = #{roleId}"
			+ " ORDER BY del_flag ASC LIMIT 1")
	SysUserRole selectAnyByUserAndRole(@Param("userId") String userId, @Param("roleId") String roleId);

	/**
	 * 恢复已逻辑删除的关联行（授予角色的幂等路径，避免重复新增历史行）
	 * @param id 关联行ID
	 * @return 影响行数
	 */
	@Update("UPDATE sys_user_role SET del_flag = '0' WHERE id = #{id} AND del_flag = '1'")
	int reviveById(@Param("id") String id);

}
