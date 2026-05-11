package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.service.ISignInRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 签到记录查询
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/signinrecord")
@Tag(description = "signinrecord", name = "签到记录查询")
public class SignInRecordController {

	private final ISignInRecordService signInRecordService;

	@Operation(summary = "签到记录分页列表")
	@SaCheckPermission("user:signinrecord:page")
	@GetMapping("/page")
	public Result page(Page page, String nickname, String beginDate, String endDate) {
		return Result.success(signInRecordService.getPage(page, nickname, beginDate, endDate));
	}

}
