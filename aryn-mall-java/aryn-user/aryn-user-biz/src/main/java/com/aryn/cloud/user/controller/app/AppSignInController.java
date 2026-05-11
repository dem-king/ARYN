package com.aryn.cloud.user.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.vo.SignInResultVO;
import com.aryn.cloud.user.service.ISignInRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端签到
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/signin")
@Tag(name = "签到-API")
public class AppSignInController {

	private final ISignInRecordService signInRecordService;

	@Operation(summary = "用户签到")
	@PostMapping
	public Result<SignInResultVO> signIn() {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(signInRecordService.signIn(userId));
	}

}
