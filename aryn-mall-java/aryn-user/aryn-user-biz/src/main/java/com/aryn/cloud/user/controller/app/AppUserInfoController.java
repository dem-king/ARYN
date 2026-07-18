
package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.MallCommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.dto.UserCreateDTO;
import com.aryn.cloud.user.api.dto.UserPasswordUpdateDTO;
import com.aryn.cloud.user.api.dto.UserPhoneUpdateDTO;
import com.aryn.cloud.user.api.dto.UserProfileUpdateDTO;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.service.IUserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/3/1 10:13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/userinfo")
@Tag(description = "userinfo", name = "商城用户-API")
public class AppUserInfoController {

	private static final RedisScript<Long> CONSUME_SMS_CODE_SCRIPT = new DefaultRedisScript<>("""
			local value = redis.call('get', KEYS[1])
			if value and value == ARGV[1] then
				return redis.call('del', KEYS[1])
			end
			return 0
			""", Long.class);

	private final IUserInfoService userInfoService;

	private final RedisTemplate<String, String> redisTemplate;

	@Operation(summary = "注册用户")
	@PostMapping("/register")
	public Result<Boolean> register(HttpServletRequest request, @RequestBody @Valid UserCreateDTO userInfo) {
		return Result.success(userInfoService.saveUser(userInfo,
				request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE)));
	}

	@Operation(summary = "获取用户信息")
	@GetMapping
	public Result<UserRespVO> getUserInfo() {
		return Result.success(userInfoService.getUserById(SecurityUtils.getUser().getUserId()));
	}

	@Operation(summary = "修改用户信息")
	@PostMapping("/update/info")
	public Result<Boolean> updateInfo(@RequestBody @Valid UserProfileUpdateDTO request) {
		return Result.success(userInfoService.updateProfile(SecurityUtils.getUser().getUserId(), request));
	}

	@Operation(summary = "用户修改手机号")
	@PostMapping("/update/phone")
	public Result updatePhone(@RequestBody @Valid UserPhoneUpdateDTO userInfoDTO) {
		String randomStr = userInfoDTO.getPhone();
		String key = CacheConstants.SMS_CODE_KEY + randomStr;
		Long consumed = redisTemplate.execute(CONSUME_SMS_CODE_SCRIPT, List.of(key), userInfoDTO.getCode());

		if (!Long.valueOf(1L).equals(consumed)) {
			return Result.fail("验证码不合法");
		}
		String userId = SecurityUtils.getUser().getUserId();
		// 查询手机号是否已存在
		long count = userInfoService.count(Wrappers.<UserInfo>lambdaQuery()
			.eq(UserInfo::getPhone, userInfoDTO.getPhone())
			.ne(UserInfo::getId, userId));
		if (count > 0) {
			return Result.fail("手机号已存在");
		}
		UserInfo userInfo = new UserInfo();
		userInfo.setId(userId);
		userInfo.setPhone(userInfoDTO.getPhone());
		return Result.success(userInfoService.updateById(userInfo));
	}

	@Operation(summary = "修改密码")
	@PostMapping("/update/password")
	public Result<Boolean> updatePassword(@RequestBody @Valid UserPasswordUpdateDTO request) {
		return Result.success(userInfoService.updatePassword(SecurityUtils.getUser().getUserId(), request));
	}

}
