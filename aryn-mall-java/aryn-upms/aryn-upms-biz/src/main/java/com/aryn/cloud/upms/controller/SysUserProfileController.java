package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.PasswordPolicy;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.api.dto.SysUserProfileDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.UploadFileValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 当前登录用户的个人信息与账号安全。
 *
 * <p>只操作会话中的登录用户，不接受客户端传入 userId：普通员工也能自助维护头像、
 * 昵称、邮箱与密码，不需要账号管理权限。修改登录账号与密码都要求校验当前密码，
 * 避免会话被劫持后直接改掉登录凭证。</p>
 *
 * <p>所有读写都在登录用户自己的租户上下文下执行：平台管理员切换租户后，请求头里的
 * 租户是“正在查看的租户”，不能用来定位或更新自己的账号。</p>
 *
 * @author aryn
 * @since 2026/10/29
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/profile")
@Tag(description = "profile", name = "个人信息")
public class SysUserProfileController {

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"^[\\w.!#$%&'*+/=?^`{|}~-]+@[\\w-]+(\\.[\\w-]+)+$");

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^\\S{3,32}$");

	private static final int NICKNAME_MAX_LENGTH = 32;

	private static final int EMAIL_MAX_LENGTH = 128;

	private final ISysUserService sysUserService;

	private final ISysStorageConfigService sysStorageConfigService;

	private final StorageFactory storageFactory;

	private final UploadFileValidator uploadFileValidator;

	@SysLog("修改个人资料")
	@Operation(summary = "修改当前用户个人资料")
	@PutMapping
	public Result updateProfile(@RequestBody SysUserProfileDTO dto) {
		ArynUser loginUser = SecurityUtils.requireUser();
		String nickname = StrUtil.trim(dto.getNickname());
		if (StrUtil.isBlank(nickname)) {
			return Result.fail("昵称不能为空");
		}
		if (nickname.length() > NICKNAME_MAX_LENGTH) {
			return Result.fail("昵称不能超过" + NICKNAME_MAX_LENGTH + "个字符");
		}
		String email = StrUtil.trimToEmpty(dto.getEmail());
		if (email.length() > EMAIL_MAX_LENGTH) {
			return Result.fail("邮箱不能超过" + EMAIL_MAX_LENGTH + "个字符");
		}
		if (StrUtil.isNotEmpty(email) && !EMAIL_PATTERN.matcher(email).matches()) {
			return Result.fail("邮箱格式不正确");
		}
		boolean updated = withOwnTenant(loginUser, () -> {
			SysUser update = new SysUser();
			update.setId(loginUser.getUserId());
			update.setNickname(nickname);
			update.setEmail(email);
			return sysUserService.updateById(update);
		});
		if (updated) {
			refreshSession(loginUser, user -> {
				user.setNickname(nickname);
				user.setEmail(email);
			});
		}
		return Result.success(updated);
	}

	@SysLog("修改登录账号")
	@Operation(summary = "修改当前用户登录账号")
	@PutMapping("/username")
	public Result updateUsername(@RequestBody SysUserProfileDTO dto) {
		ArynUser loginUser = SecurityUtils.requireUser();
		String username = StrUtil.trim(dto.getUsername());
		if (StrUtil.isBlank(username)) {
			return Result.fail("请输入新的登录账号");
		}
		if (!USERNAME_PATTERN.matcher(username).matches()) {
			return Result.fail("登录账号需为3-32个字符，且不能包含空格");
		}
		SysUser current = requireCurrentUser(loginUser);
		if (username.equals(current.getUsername())) {
			return Result.fail("新登录账号与当前账号相同");
		}
		if (!passwordMatches(dto.getPassword(), current.getPassword())) {
			return Result.fail("当前密码错误");
		}
		// 用户名的唯一约束在库上是全租户范围，这里按同样口径查重
		if (sysUserService.findUserByName(username) != null) {
			return Result.fail("该登录账号已被使用");
		}
		boolean updated = withOwnTenant(loginUser, () -> {
			SysUser update = new SysUser();
			update.setId(loginUser.getUserId());
			update.setUsername(username);
			return sysUserService.updateById(update);
		});
		if (updated) {
			refreshSession(loginUser, user -> user.setUsername(username));
		}
		return Result.success(updated);
	}

	@SysLog("修改密码")
	@Operation(summary = "修改当前用户密码")
	@PutMapping("/password")
	public Result updatePassword(@RequestBody SysUserProfileDTO dto) {
		ArynUser loginUser = SecurityUtils.requireUser();
		SysUser current = requireCurrentUser(loginUser);
		if (!passwordMatches(dto.getPassword(), current.getPassword())) {
			return Result.fail("当前密码错误");
		}
		String passwordError = PasswordPolicy.validate(dto.getNewPassword());
		if (passwordError != null) {
			return Result.fail(passwordError);
		}
		if (!dto.getNewPassword().equals(dto.getCheckPassword())) {
			return Result.fail("两次输入的新密码不一致");
		}
		if (dto.getNewPassword().equals(dto.getPassword())) {
			return Result.fail("新密码不能与当前密码相同");
		}
		boolean updated = withOwnTenant(loginUser, () -> {
			SysUser update = new SysUser();
			update.setId(loginUser.getUserId());
			update.setPassword(BCrypt.hashpw(dto.getNewPassword()));
			return sysUserService.updateById(update);
		});
		// 自助改密不撤销当前会话：前端在成功后主动退出登录，保留当前 token
		// 才能让退出接口正常执行；管理员重置密码仍由 SysUserController 全量撤销。
		return Result.success(updated);
	}

	@SysLog("修改头像")
	@Operation(summary = "上传并修改当前用户头像")
	@PostMapping("/avatar")
	public Result<String> updateAvatar(@RequestPart("file") MultipartFile file) throws Exception {
		ArynUser loginUser = SecurityUtils.requireUser();
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(loginUser.getTenantId());
		try {
			uploadFileValidator.validate(file);
			SysStorageConfigDTO storageConfig = sysStorageConfigService.getConfig();
			if (storageConfig == null) {
				throw new ArynBusinessException("当前租户未配置启用的文件存储，请联系管理员完成配置");
			}
			String url = storageFactory.getStrategy(storageConfig.getType())
				.uploadFile(storageConfig, file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
						file.getSize());
			SysUser update = new SysUser();
			update.setId(loginUser.getUserId());
			update.setAvatar(url);
			sysUserService.updateById(update);
			refreshSession(loginUser, user -> user.setAvatar(url));
			return Result.success(url);
		}
		finally {
			restoreTenant(previousTenantId);
		}
	}

	private SysUser requireCurrentUser(ArynUser loginUser) {
		SysUser current = withOwnTenant(loginUser, () -> sysUserService.getById(loginUser.getUserId()));
		if (current == null) {
			throw new ArynBusinessException("当前账号信息不存在");
		}
		return current;
	}

	/**
	 * 密码比对：空密码、库中 hash 非法都按“不匹配”处理，避免 BCrypt 抛异常后返回 500。
	 */
	private boolean passwordMatches(String rawPassword, String passwordHash) {
		if (StrUtil.isBlank(rawPassword) || StrUtil.isBlank(passwordHash)) {
			return false;
		}
		try {
			return BCrypt.checkpw(rawPassword, passwordHash);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private <T> T withOwnTenant(ArynUser loginUser, Supplier<T> action) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(loginUser.getTenantId());
		try {
			return action.get();
		}
		finally {
			restoreTenant(previousTenantId);
		}
	}

	private void restoreTenant(String previousTenantId) {
		if (StrUtil.isBlank(previousTenantId)) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

	/**
	 * 同步刷新会话中的用户快照，让顶部头像/用户名立即生效。
	 * <p>刷新失败不影响已落库的资料，下次登录会重新读取。</p>
	 */
	private void refreshSession(ArynUser loginUser, Consumer<ArynUser> mutator) {
		mutator.accept(loginUser);
		try {
			StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, loginUser);
		}
		catch (Exception e) {
			// token 是 bearer 凭证，日志只记录失败原因
			log.warn("刷新当前会话用户信息失败: {}", e.getMessage());
		}
	}

}
