
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.upms.api.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

	/**
	 * 通过用户id查询角色
	 *
	 * @author 雨滴kian
	 * @date 2022/8/23
	 * @param userId
	 * @return: java.util.List<java.lang.String>
	 */
	List<String> listRoleIdsByUserId(String userId);

	/**
	 * 分页查询
	 * @param page
	 * @param sysRole
	 * @return
	 */
	IPage<SysRole> selectRolePage(Page page, @Param("query") SysRole sysRole);

	/**
	 * 查询平台管理员角色 ID（租户上下文由拦截器限定）。
	 *
	 * <p>单独成方法而不是用通用 wrapper 查询：平台管理员角色是受保护角色，
	 * 授予校验需要稳定且可被单元测试隔离的查询入口。</p>
	 */
	@Select("SELECT `id` FROM `sys_role` WHERE `role_code` = 'ROLE_PLATFORM_ADMIN' AND `del_flag` = '0'")
	List<String> selectPlatformAdminRoleIds();

}
