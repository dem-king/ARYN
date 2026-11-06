
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.dto.SysUserDTO;
import com.aryn.cloud.upms.api.dto.SysUserPhoneUpdateDTO;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.api.vo.SysUserVO;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import com.aryn.cloud.common.security.util.PasswordPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

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

	/** 手机号格式：11 位大陆手机号 */
	private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

	private final ISysUserService sysUserService;

	private final ISysRoleService sysRoleService;

	private final ISysUserRoleService sysUserRoleService;

	private final SysUserSessionRevoker sysUserSessionRevoker;

	@DubboReference
	private final RemoteDeliveryAccountService remoteDeliveryAccountService;

	@Operation(summary = "获取当前用户全部信息")
	@GetMapping("/info")
	public Result<SysUserVO> info() {
		SysUser sysUser = sysUserService.getById(StpUtil.getLoginId().toString());
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户信息为空");
		}
		// HTTP 响应不含密码 hash：实体只服务于内部认证链路
		return Result.success(SysUserVO.from(sysUserService.findUserInfo(sysUser)));
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
	public Result<SysUserVO> getById(@PathVariable String id) {
		// 平台管理员账号是系统内置账号，走可见性查询：租户管理员拿到 ID 也读不到
		SysUser sysUser = sysUserService.getVisibleUserById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在");
		}
		sysUser.setRoles(sysRoleService.findRoleIdsByUserId(id));
		return Result.success(SysUserVO.from(sysUser));
	}

	/**
	 * 查看员工完整手机号：列表与详情响应一律脱敏，联系同事需要完整号码时单独取，
	 * 由独立权限控制并写入操作日志，日志不记录号码本身。
	 */
	@SysLog("查看员工完整手机号")
	@Operation(summary = "查看员工完整手机号")
	@SaCheckPermission("upms:sysuser:phone")
	@GetMapping("/{id}/phone")
	public Result<String> phone(@PathVariable String id) {
		SysUser sysUser = sysUserService.getVisibleUserById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return Result.fail("用户不存在");
		}
		if (StrUtil.isBlank(sysUser.getPhone())) {
			return Result.fail("该账号未绑定手机号");
		}
		return Result.success(sysUser.getPhone());
	}

	/**
	 * 修改员工手机号：手机号是管理端短信登录与配送员登录的凭据，按「重置密码」同敏感度管理，
	 * 使用独立权限与操作日志，日志不记录号码本身。
	 *
	 * <p>旧号丢失是常见场景，管理员入口不要求员工短信验证码；脱敏值永不回写，
	 * 服务端强制校验完整号码。更新成功后 best-effort 同步配送员资料联系电话。</p>
	 */
	@SysLog("修改员工手机号")
	@Operation(summary = "修改员工手机号")
	@SaCheckPermission("upms:sysuser:phone:edit")
	@PutMapping("/{id}/phone")
	public Result<Boolean> updatePhone(@PathVariable String id, @RequestBody SysUserPhoneUpdateDTO dto) {
		if (dto == null) {
			return Result.fail("请输入手机号");
		}
		String phone = StrUtil.trim(dto.getPhone());
		if (StrUtil.isBlank(phone)) {
			return Result.fail("请输入手机号");
		}
		if (!PHONE_PATTERN.matcher(phone).matches()) {
			return Result.fail("手机号格式不正确");
		}
		// 租户拦截器把查询限定在当前查看的租户内；平台管理员账号是系统内置账号，
		// 不在员工账号页面暴露，也不允许经此入口改绑手机号（手机号可直接用于短信登录）
		SysUser target = sysUserService.getVisibleUserById(id);
		if (ObjectUtil.isNull(target)) {
			return Result.fail("用户不存在或已删除");
		}
		if (phone.equals(target.getPhone())) {
			return Result.fail("新手机号与当前手机号相同");
		}
		// 唯一约束覆盖全部租户（del_flag='0'），查重必须与服务端唯一口径一致
		SysUser query = new SysUser();
		query.setPhone(phone);
		if (sysUserService.getCount(query) > 0) {
			return Result.fail("该手机号已被使用");
		}
		SysUser update = new SysUser();
		update.setId(id);
		update.setPhone(phone);
		boolean updated;
		try {
			updated = sysUserService.updateById(update);
		}
		catch (DuplicateKeyException e) {
			// 唯一键冲突消息会带出完整手机号，日志只记录目标账号
			log.warn("修改员工手机号触发唯一键冲突：userId={}", id);
			return Result.fail("该手机号已被使用");
		}
		if (!updated) {
			return Result.fail("手机号修改失败");
		}
		syncDeliveryStaffPhone(id, phone);
		return Result.success(true);
	}

	/**
	 * 配送员资料联系电话 best-effort 同步：员工账号手机号已落库并即时生效，
	 * 同步失败只告警，不回滚、不阻断接口返回。
	 */
	private void syncDeliveryStaffPhone(String userId, String phone) {
		try {
			remoteDeliveryAccountService.syncStaffPhone(userId, phone);
		}
		catch (Exception e) {
			log.warn("修改员工手机号后同步配送员联系电话失败：userId={}，原因={}", userId, e.getMessage());
		}
	}

	@SysLog("新增用户")
	@Operation(summary = "用户新增")
	@SaCheckPermission("upms:sysuser:add")
	@PostMapping
	public Result add(@RequestBody SysUser sysUser) {
		if (ArrayUtil.isEmpty(sysUser.getRoles())) {
			return Result.fail("角色不能为空");
		}
		// 服务端密码强度校验（前端表单提示不能替代）：8~64 位、字母+数字、非弱口令
		String passwordError = PasswordPolicy.validate(sysUser.getPassword());
		if (passwordError != null) {
			return Result.fail(passwordError);
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
		if (StrUtil.isBlank(sysUserDTO.getPassword())
				|| !BCrypt.checkpw(sysUserDTO.getPassword(), sysUser.getPassword())) {
			return Result.fail("旧密码错误");
		}
		// 服务端密码强度校验：空串/极短/纯数字/弱口令一律拒绝
		String passwordError = PasswordPolicy.validate(sysUserDTO.getNewPassword());
		if (passwordError != null) {
			return Result.fail(passwordError);
		}
		if (!sysUserDTO.getNewPassword().equals(sysUserDTO.getCheckPassword())) {
			return Result.fail("新密码与确认密码不一致");
		}
		sysUser.setPassword(BCrypt.hashpw(sysUserDTO.getNewPassword()));
		boolean updated = sysUserService.updateById(sysUser);
		if (updated) {
			// 改密即失效：撤销该账号全部已登录会话，避免旧 token 用旧密码的会话继续有效
			sysUserSessionRevoker.revokeAll(sysUser.getId(), SessionInvalidSubReason.PASSWORD_CHANGED);
		}
		return Result.success(updated);
	}

}
