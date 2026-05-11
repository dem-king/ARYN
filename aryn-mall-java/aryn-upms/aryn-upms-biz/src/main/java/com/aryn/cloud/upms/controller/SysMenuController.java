
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.vo.MenuVO;
import com.aryn.cloud.upms.service.ISysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:40
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/menu")
@Tag(description = "menu", name = "菜单管理")
public class SysMenuController {

	private final ISysMenuService sysMenuService;

	/**
	 * 获取所有菜单和权限
	 * @return 树形菜单
	 */
	@Operation(summary = "查询树形菜单集合")
	@SaCheckPermission("upms:sysmenu:page")
	@GetMapping(value = "/tree")
	public Result getAllTree() {
		List<SysMenu> sysMenus = sysMenuService.list(Wrappers.<SysMenu>lambdaQuery());
		List<TreeNode<String>> treeNodes = sysMenus.stream()
			.sorted(Comparator.comparingInt(SysMenu::getSort))
			.map(menu -> {
				TreeNode<String> treeNode = new TreeNode<>();
				treeNode.setId(menu.getId());
				treeNode.setParentId(menu.getParentId());
				treeNode.setName(menu.getName());
				treeNode.setWeight(menu.getSort());
				Map<String, Object> extra = new HashMap<>();
				extra.put("icon", menu.getIcon());
				extra.put("type", menu.getType());
				extra.put("createTime", menu.getCreateTime());
				extra.put("permission", menu.getPermission());
				extra.put("path", menu.getPath());
				extra.put("component", menu.getComponent());
				extra.put("applicationKey", menu.getApplicationKey());
				extra.put("sort", menu.getSort());
				treeNode.setExtra(extra);
				return treeNode;
			})
			.collect(Collectors.toList());
		return Result.success(TreeUtil.build(treeNodes, CommonConstants.PARENT_ID));
	}

	@Operation(summary = "查询当前用户的树形菜单集合")
	@GetMapping
	public Result getMenu() {
		List<TreeNode<String>> treeNodes = sysMenuService.getLoginUserMenuTree();
		return Result.success(TreeUtil.build(treeNodes, CommonConstants.PARENT_ID));
	}

	@Operation(summary = "查询租户树形菜单集合")
	@GetMapping("/tenant")
	public Result getTenantMenu() {
		List<TreeNode<String>> treeNodes = sysMenuService.getTenantMenuTree(ArynTenantContextHolder.getTenantId());
		return Result.success(TreeUtil.build(treeNodes, CommonConstants.PARENT_ID));
	}

	@Operation(summary = "查询指定角色菜单")
	@GetMapping("/role/{roleId}")
	public Result<List<MenuVO>> getRoleMenu(@PathVariable("roleId") String roleId) {
		return Result.success(sysMenuService.findMenuByRoleId(roleId));
	}

	@Operation(summary = "通过ID查询")
	@SaCheckPermission("upms:sysmenu:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysMenuService.getById(id));
	}

	@SysLog("新增菜单")
	@Operation(summary = "菜单新增")
	@SaCheckPermission("upms:sysmenu:add")
	@PostMapping
	public Result add(@RequestBody SysMenu sysMenu) {
		return Result.success(sysMenuService.saveMenu(sysMenu));
	}

	@SysLog("编辑菜单")
	@Operation(summary = "菜单编辑")
	@SaCheckPermission("upms:sysmenu:edit")
	@PutMapping
	public Result edit(@RequestBody SysMenu sysMenu) {
		return Result.success(sysMenuService.updateMenuById(sysMenu));
	}

	@SysLog("删除菜单")
	@Operation(summary = "菜单删除")
	@SaCheckPermission("upms:sysmenu:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		return Result.success(sysMenuService.removeMenuById(id));
	}

}
