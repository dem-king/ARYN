
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:36
 */
public interface ISysMaterialGroupService extends IService<SysMaterialGroup> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysMaterialGroup
	 * @return
	 */
	IPage<SysMaterialGroup> getPage(Page page, SysMaterialGroup sysMaterialGroup);

	/**
	 * 查询全部
	 * @param sysMaterialGroup
	 * @return
	 */
	List<SysMaterialGroup> getList(SysMaterialGroup sysMaterialGroup);

	/**
	 * 确保租户的默认分组存在，返回其 id（懒创建）。
	 *
	 * <p>「未分组」不再是前端虚拟行，而是每租户一条真实分组记录。查询分组列表与写入素材
	 * 都会走这里，因此默认分组的创建时机收敛为「第一次被需要时」——新租户无需在建租户
	 * 事务里同步补数据（也避免为它改 {@code SysTenantServiceImpl} 的构造参数）。</p>
	 * @param tenantId 租户 id，为空时取当前会话租户
	 * @return 默认分组 id
	 */
	String ensureDefaultGroupId(String tenantId);

	/**
	 * 把请求里的 groupId 解析成可以落库的分组 id。
	 *
	 * <p>以下情况一律回落到当前租户的默认分组：为空、为历史虚拟值 '-1'、指向不存在/
	 * 已逻辑删除/属于其它租户的分组。素材因此永远不会再挂在不存在或跨租户的分组上。</p>
	 * @param tenantId 租户 id，为空时取当前会话租户
	 * @param requestedGroupId 请求携带的分组 id
	 * @return 可落库的分组 id
	 */
	String resolveGroupIdForSave(String tenantId, String requestedGroupId);

	/**
	 * 删除分组：默认分组不可删除；普通分组先把组内素材改挂默认分组再逻辑删除。
	 * @param id 分组 id
	 * @return true 表示删除成功
	 */
	boolean removeGroup(String id);

}
