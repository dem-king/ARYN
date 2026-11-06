
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.upms.api.vo.SysUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 系统用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:50
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

	/**
	 * 用户列表查询（返回不含密码的响应 VO）
	 * @param page
	 * @param sysUser
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.upms.api.vo.SysUserVO>
	 */
	IPage<SysUserVO> adminPage(Page page, @Param("query") SysUser sysUser);

	@InterceptorIgnore(tenantLine = "true")
	SysUser selectUserByName(@Param("username") String username);

	/**
	 * 按条件统计账号数（手机号唯一性校验用，跨租户统计）。
	 * <p>
	 * 方法名不得叫 {@code selectCount}：MyBatis 语句 id = 命名空间 + 方法名，与
	 * {@code BaseMapper#selectCount(Wrapper)} 同名会让本方法的 XML 语句覆盖内置实现，
	 * 导致 {@code IService#count()} 绑不到 {@code ew} 参数抛 BindingException。
	 */
	@InterceptorIgnore(tenantLine = "true")
	int countUserByQuery(@Param("query") SysUser sysUser);

	@InterceptorIgnore(tenantLine = "true")
	SysUser selectUserByPhone(@Param("phone") String phone);

	List<StaffMessageRecipientVO> selectMessageRecipients(@Param("query") StaffMessageAudienceRequest request,
			@Param("fetchSize") int fetchSize);

	int countCustomerServiceStaff(@Param("tenantId") String tenantId, @Param("staffId") String staffId);

	/**
	 * 读取账号的平台管理员标记（1 是 / 0 否）。
	 *
	 * <p>{@code platform_admin} 是用户级平台身份列，不映射到实体，避免经通用新增/编辑接口
	 * 被客户端改写；租户拦截器会按当前上下文追加 tenant_id 条件。</p>
	 */
	@Select("SELECT `platform_admin` FROM `sys_user` WHERE `id` = #{userId} AND `del_flag` = '0'")
	String selectPlatformAdminFlag(@Param("userId") String userId);

	/**
	 * 平台管理员账号用户名集合。
	 *
	 * <p>登录日志/在线记录只保存用户名，审计类旁路视图按用户名集合过滤平台管理员；
	 * 租户拦截器会按当前上下文追加 tenant_id 条件（平台管理员只在平台租户内）。</p>
	 */
	@Select("SELECT `username` FROM `sys_user` WHERE `platform_admin` = '1' AND `del_flag` = '0'")
	List<String> selectPlatformAdminUsernames();

	/**
	 * 员工账号搜索（配送员开户、员工选择器等租户内入口），排除平台管理员账号。
	 */
	List<SysUser> searchNormalStaff(@Param("keyword") String keyword, @Param("limit") int limit);

	/**
	 * 查询租户内平台管理员账号 ID；{@code ids} 为空时返回租户内全部。
	 *
	 * <p>下游员工/业务视图（销售员、配送员等）需要按 ID 排除平台管理员，避免系统身份
	 * 被当作租户员工展示或绑定。</p>
	 */
	List<String> selectPlatformAdminIds(@Param("ids") Collection<String> ids);

	/**
	 * 当前租户管理端员工账号数（排除平台管理员；租户拦截器追加 tenant_id）。
	 */
	long countTenantStaff();

}
