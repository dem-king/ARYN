
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import com.aryn.cloud.upms.mapper.SysMaterialGroupMapper;
import com.aryn.cloud.upms.mapper.SysMaterialMapper;
import com.aryn.cloud.upms.service.ISysMaterialGroupService;
import com.aryn.cloud.upms.support.MaterialDefaultGroupSupport;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysMaterialGroupServiceImpl extends ServiceImpl<SysMaterialGroupMapper, SysMaterialGroup>
		implements ISysMaterialGroupService {

	private final SysMaterialMapper materialMapper;

	@Override
	public IPage<SysMaterialGroup> getPage(Page page, SysMaterialGroup sysMaterialGroup) {
		// 先确保默认分组存在：新租户/历史租户第一次进分组管理页时就补上，
		// 前端不再需要 unshift 虚拟「未分组」行。ensure 失败不应让列表整体不可用
		// （比如库还没跑 221 增量脚本、缺 is_default 列），降级为只打日志
		ensureDefaultGroupQuietly();
		return baseMapper.selectMaterialGroupPage(page, sysMaterialGroup);
	}

	@Override
	public List<SysMaterialGroup> getList(SysMaterialGroup sysMaterialGroup) {
		ensureDefaultGroupQuietly();
		return baseMapper.selectMaterialGroupList(sysMaterialGroup);
	}

	@Override
	public String ensureDefaultGroupId(String tenantId) {
		String scopeTenantId = requireTenantId(tenantId);
		String defaultGroupId = MaterialDefaultGroupSupport.defaultGroupId(scopeTenantId);
		// 显式传入的租户与会话租户不一致时（如平台管理员切换租户），必须临时切上下文：
		// 租户拦截器会给 insert 的 tenant_id 与查询的 WHERE 都套上会话租户，
		// 不切的话会写成别人的默认分组 / 查不到自己那条
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		boolean switched = !scopeTenantId.equals(previousTenantId);
		if (switched) {
			ArynTenantContextHolder.setTenantId(scopeTenantId);
		}
		try {
			return ensureDefaultGroupIdInScope(scopeTenantId, defaultGroupId);
		}
		finally {
			if (switched) {
				// 还原为调用前的值（可能是 null），避免污染同线程后续 SQL
				if (previousTenantId == null) {
					ArynTenantContextHolder.removeTenantId();
				}
				else {
					ArynTenantContextHolder.setTenantId(previousTenantId);
				}
			}
		}
	}

	private String ensureDefaultGroupIdInScope(String scopeTenantId, String defaultGroupId) {
		// selectById 带 @TableLogic 过滤，因此这里只会查到「未删除」的默认分组
		if (baseMapper.selectById(defaultGroupId) != null) {
			// 已存在就直接复用：分组名允许运营改过，不能覆盖
			return defaultGroupId;
		}
		SysMaterialGroup defaultGroup = new SysMaterialGroup();
		defaultGroup.setId(defaultGroupId);
		defaultGroup.setName(MaterialDefaultGroupSupport.DEFAULT_GROUP_NAME);
		defaultGroup.setType(MaterialDefaultGroupSupport.DEFAULT_GROUP_TYPE);
		defaultGroup.setSort(MaterialDefaultGroupSupport.DEFAULT_GROUP_SORT);
		defaultGroup.setIsDefault(SysMaterialGroup.DEFAULT_YES);
		defaultGroup.setDelFlag("0");
		// 显式写租户：懒创建可能发生在无租户上下文的任务线程里，不能依赖拦截器自动填充
		defaultGroup.setTenantId(scopeTenantId);
		defaultGroup.setCreateBy("system");
		try {
			baseMapper.insert(defaultGroup);
			log.info("创建素材默认分组 tenantId={} groupId={}", scopeTenantId, defaultGroupId);
		}
		catch (DuplicateKeyException e) {
			// 并发下另一个请求已建好（主键冲突或函数唯一索引 uk_material_group_default_tenant 冲突）：
			// 视为成功，再查一次确认即可，不能把并发当成业务失败抛给前端
			log.info("素材默认分组已被并发创建，直接复用 tenantId={} groupId={}", scopeTenantId, defaultGroupId);
			if (baseMapper.selectById(defaultGroupId) == null) {
				// 行存在却查不到，只剩「默认分组被逻辑删除」这一种可能（主键占用 + del_flag='1'）。
				// 此时不能返回这个 id：新素材会挂到一个任何列表都看不到的分组上。
				log.error("素材默认分组已被逻辑删除，需人工恢复 tenantId={} groupId={}", scopeTenantId, defaultGroupId);
				throw new ArynBusinessException("素材默认分组已被删除，请执行素材默认分组增量脚本或人工恢复该分组");
			}
		}
		return defaultGroupId;
	}

	@Override
	public String resolveGroupIdForSave(String tenantId, String requestedGroupId) {
		String scopeTenantId = requireTenantId(tenantId);
		if (isMissingGroupId(requestedGroupId)) {
			return ensureDefaultGroupId(scopeTenantId);
		}
		SysMaterialGroup target = baseMapper.selectById(requestedGroupId);
		// 不存在、已逻辑删除、或属于其它租户的分组都不能作为落库目标
		if (target == null || isDeleted(target) || !scopeTenantId.equals(target.getTenantId())) {
			log.info("素材分组不存在或不可用，回落到默认分组 tenantId={} requestedGroupId={}", scopeTenantId,
					requestedGroupId);
			return ensureDefaultGroupId(scopeTenantId);
		}
		return target.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeGroup(String id) {
		if (id == null || id.isBlank()) {
			throw new ArynBusinessException("分组 id 不能为空");
		}
		String tenantId = requireTenantId(null);
		SysMaterialGroup group = baseMapper.selectById(id);
		if (group == null || isDeleted(group)) {
			throw new ArynBusinessException("分组不存在或已删除");
		}
		// 默认分组是素材的兜底归属，删掉后组内素材无处可挂，因此不可删除（可改名）
		if (MaterialDefaultGroupSupport.isDefault(group)
				|| MaterialDefaultGroupSupport.isDefaultGroupId(group.getId(), tenantId)) {
			throw new ArynBusinessException("默认分组不可删除");
		}
		String defaultGroupId = ensureDefaultGroupId(tenantId);
		// 先改挂再删分组：顺序反了会出现「素材指向已删除分组」的中间态
		int moved = materialMapper.update(null, Wrappers.<SysMaterial>lambdaUpdate()
			.set(SysMaterial::getGroupId, defaultGroupId)
			.eq(SysMaterial::getGroupId, group.getId()));
		if (moved > 0) {
			log.info("删除素材分组前改挂素材到默认分组 tenantId={} groupId={} defaultGroupId={} moved={}", tenantId,
					group.getId(), defaultGroupId, moved);
		}
		return baseMapper.deleteById(group.getId()) > 0;
	}

	/**
	 * 列表前置的默认分组补齐。
	 *
	 * <p>列表是只读入口，懒创建失败（缺列、并发异常等）只应让「默认分组还没出现」，
	 * 不应把整个分组管理页打成 500——运营至少还能看到已有分组并改名。</p>
	 */
	private void ensureDefaultGroupQuietly() {
		try {
			ensureDefaultGroupId(ArynTenantContextHolder.getTenantId());
		}
		catch (RuntimeException e) {
			log.warn("查询素材分组前补齐默认分组失败，本次仅返回已有分组", e);
		}
	}

	/**
	 * 解析租户：显式传入优先，其次取当前会话租户。两者都为空说明调用链缺少租户上下文，
	 * 无法确定默认分组归属，直接失败而不是写出一条 tenant_id 为空的脏数据。
	 */
	private String requireTenantId(String tenantId) {
		String scopeTenantId = tenantId == null || tenantId.isBlank() ? ArynTenantContextHolder.getTenantId()
				: tenantId;
		if (scopeTenantId == null || scopeTenantId.isBlank()) {
			throw new ArynBusinessException("缺少租户上下文，无法定位素材默认分组");
		}
		return scopeTenantId;
	}

	private boolean isMissingGroupId(String groupId) {
		return groupId == null || groupId.isBlank() || SysMaterialGroup.LEGACY_UNGROUPED_ID.equals(groupId.trim());
	}

	private boolean isDeleted(SysMaterialGroup group) {
		return group.getDelFlag() != null && !"0".equals(group.getDelFlag());
	}

}
