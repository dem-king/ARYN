
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.dto.SysUserDTO;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 员工账号管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:45
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/user")
@Tag(description = "user", name = "员工账号管理")
public class SysUserController {

	private final ISysUserService sysUserService;

	private final ISysRoleService sysRoleService;

	private final ISysUserRoleService sysUserRoleService;

	@DubboReference
	private final RemoteDeliveryAccountService remoteDeliveryAccountService;

	@Operation(summary = "获取当前用户全部信息")
	@GetMapping("/info")
	public Result info() {
		SysUser sysUser = sysUserService.getById(StpUtil.getLoginId().toString());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户信息为空");
		}
		return Result.success(sysUserService.findUserInfo(sysUser));
	}

	@Operation(summary = "用户列表")
	@SaCheckPermission("upms:sysuser:page")
	@GetMapping("/page")
	public Result page(Page page, SysUser sysUser) {
		return Result.success(sysUserService.adminPage(page, sysUser));
	}

	@Operation(summary = "通过id查询用户信息")
	@SaCheckPermission("upms:sysuser:get")
	@GetMapping("/{id}")
	public Result<SysUser> getById(@PathVariable String id) {
		SysUser sysUser = sysUserService.getById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在");
		}
		sysUser.setRoles(sysRoleService.findRoleIdsByUserId(id));
		return Result.success(sysUser);
	}

	@SysLog("新增用户")
	@Operation(summary = "用户新增")
	@SaCheckPermission("upms:sysuser:add")
	@PostMapping
	public Result add(@RequestBody SysUser sysUser) {
		if (ArrayUtil.isEmpty(sysUser.getRoles())) {
			return Result.fail("角色不能为空");
		}
		sysUser.setPassword(BCrypt.hashpw(sysUser.getPassword()));
		return Result.success(sysUserService.saveUser(sysUser));
	}

	@SysLog("修改用户")
	@Operation(summary = "用户修改")
	@SaCheckPermission("upms:sysuser:edit")
	@PutMapping
	public Result edit(@RequestBody SysUser sysUser) {
		sysUser.setPassword(null);
		sysUser.setPhone(null);
		// 角色非空与受保护配送角色合并由服务层处理：编辑时保留已有配送资格
		return Result.success(sysUserService.updateUser(sysUser));
	}

	@SysLog("删除用户")
	@Operation(summary = "用户删除")
	@SaCheckPermission("upms:sysuser:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		// 查询用户
		SysUser sysUser = sysUserService.getById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在或已删除");
		}
		// 查询管理员角色
		SysRole sysRole = sysRoleService
			.getOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, CommonConstants.ROLE_ADMIN_CODE));
		long count = sysUserRoleService.count(Wrappers.<SysUserRole>lambdaQuery()
			.eq(SysUserRole::getRoleId, sysRole.getId())
			.eq(SysUserRole::getUserId, sysUser.getId()));
		if (count > 0) {
			return Result.fail("管理员不允许删除");
		}
		// 删除保护：仍有关联配送资料时必须先在配送员管理清理，避免产生孤儿资料；
		// 远程检查失败时 fail-closed 拒绝删除
		boolean hasDeliveryStaff;
		try {
			hasDeliveryStaff = remoteDeliveryAccountService.hasActiveDeliveryStaff(sysUser.getId());
		}
		catch (Exception e) {
			log.error("删除员工账号前确认配送关联失败，拒绝删除：userId={}", sysUser.getId(), e);
			return Result.fail("无法确认配送关联，请稍后重试");
		}
		if (hasDeliveryStaff) {
			return Result.fail("该员工账号仍关联配送员资料，请先在配送员管理中删除或停用配送员资料");
		}
		return Result.success(sysUserService.delUser(sysUser));
	}

	@Operation(summary = "验证手机号")
	@GetMapping("/check/phone")
	public Result checkPhone(SysUser sysUser) {
		int count = sysUserService.getCount(sysUser);
		return Result.success(count);
	}

	@SysLog("修改密码")
	@Operation(summary = "密码修改")
	@SaCheckPermission("upms:sysuser:password")
	@PostMapping("/password")
	public Result editPwd(@RequestBody SysUserDTO sysUserDTO) {
		SysUser sysUser = sysUserService.getById(sysUserDTO.getId());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在或已删除");
		}
		if (!BCrypt.checkpw(sysUserDTO.getPassword(), sysUser.getPassword())) {
			return Result.fail("旧密码错误");
		}
		if (!sysUserDTO.getNewPassword().equals(sysUserDTO.getCheckPassword())) {
			return Result.fail("新密码与确认密码不一致");
		}
		sysUser.setPassword(BCrypt.hashpw(sysUserDTO.getNewPassword()));
		return Result.success(sysUserService.updateById(sysUser));
	}

}
