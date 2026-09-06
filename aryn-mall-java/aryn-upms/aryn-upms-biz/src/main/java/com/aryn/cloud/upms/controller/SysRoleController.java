
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.upms.api.dto.SysRoleMenuDTO;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysRoleMenu;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.service.ISysRoleMenuService;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理
 *
 * @author 雨滴kian
 * @since 2022/2/10 16:17
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/sysrole")
@Tag(description = "sysrole", name = "角色管理")
public class SysRoleController {

	private final ISysRoleService sysRoleService;

	private final ISysUserRoleService sysUserRoleService;

	private final ISysRoleMenuService sysRoleMenuService;

	@Operation(summary = "角色列表")
	@SaCheckPermission("upms:sysrole:page")
	@GetMapping("/page")
	public Result<IPage<SysRole>> page(Page page, SysRole sysRole) {
		return Result.success(sysRoleService.getPage(page, sysRole));
	}

	@Operation(summary = "查询全部角色")
	@SaCheckPermission("upms:sysrole:get")
	@GetMapping("/list")
	public Result<List<SysRole>> getList(SysRole sysRole) {
		// roleCode 供前端识别受保护角色（如 delivery_staff）并在员工账号表单中过滤展示
		return Result.success(
				sysRoleService.list(Wrappers.query(sysRole).lambda().select(SysRole::getRoleName, SysRole::getRoleCode, SysRole::getId)));
	}

	@Operation(summary = "角色查询")
	@SaCheckPermission("upms:sysrole:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(sysRoleService.getById(id));
	}

	@Operation(summary = "角色新增")
	@SysLog("新增角色")
	@SaCheckPermission("upms:sysrole:add")
	@PostMapping
	public Result add(@RequestBody SysRole sysRole) {
		if (sysRoleService.checkRole(new SysRole(null, sysRole.getRoleCode()))) {
			return Result.fail("角色编码已存在");
		}
		if (sysRoleService.checkRole(new SysRole(sysRole.getRoleName(), null))) {
			return Result.fail("角色名称已存在");
		}
		return Result.success(sysRoleService.save(sysRole));
	}

	@SysLog("修改角色")
	@Operation(summary = "角色修改")
	@SaCheckPermission("upms:sysrole:edit")
	@PutMapping
	public Result edit(@RequestBody SysRole sysRole) {
		SysRole exists = sysRoleService.getById(sysRole.getId());
		if (exists != null && CommonConstants.PROTECTED_DELIVERY_ROLE_CODE.equals(exists.getRoleCode())
				&& StrUtil.isNotBlank(sysRole.getRoleCode())
				&& !CommonConstants.PROTECTED_DELIVERY_ROLE_CODE.equals(sysRole.getRoleCode())) {
			return Result.fail("配送员资格角色编码不可修改，配送资格请在配送员管理中开通或停用");
		}
		if (sysRoleService.checkRole(new SysRole(sysRole.getId(), sysRole.getRoleName(), null))) {
			return Result.fail("角色名称已存在");
		}
		return Result.success(sysRoleService.updateById(sysRole));
	}

	@Operation(summary = "角色删除")
	@SaCheckPermission("upms:sysrole:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		SysRole exists = sysRoleService.getById(id);
		if (exists == null) {
			return Result.fail("角色不存在");
		}
		// 受保护角色只能由配送员管理（资格接口/向导）授予或回收，删除会导致资格链路中断
		if (CommonConstants.PROTECTED_DELIVERY_ROLE_CODE.equals(exists.getRoleCode())) {
			return Result.fail("配送员资格角色不可删除，配送资格请在配送员管理中开通或停用");
		}
		long count = sysUserRoleService.count(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
		if (count > 0) {
			return Result.fail("该角色已被用户绑定，不可删除");
		}
		// 删除角色关联菜单
		sysRoleMenuService.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
		return Result.success(sysRoleService.removeById(id));
	}

	@Operation(summary = "更新角色菜单")
	@SaCheckPermission("upms:sysrole:update")
	@PostMapping("/role/menu")
	public Result saveRoleMenu(@RequestBody SysRoleMenuDTO request) {
		return Result.success(sysRoleMenuService.saveRoleMenu(request));
	}

}
