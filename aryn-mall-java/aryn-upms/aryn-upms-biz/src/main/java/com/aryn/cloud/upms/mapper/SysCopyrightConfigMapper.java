package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.upms.api.entity.SysCopyrightConfig;
import com.aryn.cloud.upms.api.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 版权配置。
 *
 * <p>登录页在登录前就要展示品牌与版权，此时没有会话、租户上下文为空，租户插件会拼出
 * {@code tenant_id = NULL} 而静默查不到数据。因此读取一律走显式带 tenant_id 的语句并
 * 关闭租户插件（与 PayConfigMapper 同口径）；租户归属由服务层决定，不接受请求方指定。</p>
 */
@Mapper
public interface SysCopyrightConfigMapper extends BaseMapper<SysCopyrightConfig> {

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM sys_copyright_config
			WHERE tenant_id = #{tenantId} AND client_type = #{clientType} AND del_flag = '0'
			LIMIT 1
			""")
	SysCopyrightConfig selectByTenantAndClient(@Param("tenantId") String tenantId,
			@Param("clientType") String clientType);

	/**
	 * 平台租户 ID。平台身份是数据驱动的（196 号脚本把悦航购提升为唯一平台租户），
	 * 代码里不得再出现写死的租户 ID；登录前无租户上下文，只能按类型反查。
	 */
	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT id FROM sys_tenant
			WHERE tenant_type = '1' AND del_flag = '0'
			ORDER BY create_time, id
			LIMIT 1
			""")
	String selectPlatformTenantId();

	/**
	 * 按 ID 取租户品牌（名称/Logo）。
	 *
	 * <p>必须关掉租户插件并显式带 id：登录页读取时租户上下文为空，走
	 * {@code selectById} 会被拼成 {@code tenant_id = NULL} 而静默查不到，
	 * 表现为平台登录页品牌回落成内置文案。</p>
	 */
	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM sys_tenant
			WHERE id = #{tenantId} AND del_flag = '0'
			LIMIT 1
			""")
	SysTenant selectTenantBrand(@Param("tenantId") String tenantId);

	/**
	 * 全字段覆盖保存。可编辑列必须全部写回：运营清空名称/Logo 表示「回落租户品牌」，
	 * 若沿用 updateById 的非空策略，清空的字段会被静默忽略、旧值长期残留。
	 */
	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE sys_copyright_config
			SET logo_url = #{logoUrl},
				name = #{name},
				copyright_text = #{copyrightText},
				copyright_link = #{copyrightLink},
				icp = #{icp},
				icp_link = #{icpLink},
				login_title = #{loginTitle},
				login_subtitle = #{loginSubtitle},
				login_icon = #{loginIcon},
				update_by = #{updateBy},
				update_time = NOW()
			WHERE id = #{id} AND tenant_id = #{tenantId} AND del_flag = '0'
			""")
	int overwriteByTenantAndId(@Param("id") String id, @Param("tenantId") String tenantId,
			@Param("logoUrl") String logoUrl, @Param("name") String name,
			@Param("copyrightText") String copyrightText, @Param("copyrightLink") String copyrightLink,
			@Param("icp") String icp, @Param("icpLink") String icpLink,
			@Param("loginTitle") String loginTitle, @Param("loginSubtitle") String loginSubtitle,
			@Param("loginIcon") String loginIcon, @Param("updateBy") String updateBy);

}