package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.SignInConfig;
import com.aryn.cloud.user.api.vo.AppSignInConfigVO;
import com.aryn.cloud.user.api.vo.AppSignInRecordVO;
import com.aryn.cloud.user.api.vo.SignInResultVO;
import com.aryn.cloud.user.service.ISignInConfigService;
import com.aryn.cloud.user.service.ISignInRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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

	private final ISignInConfigService signInConfigService;

	@Operation(summary = "用户签到")
	@PostMapping
	public Result<SignInResultVO> signIn() {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(signInRecordService.signIn(userId));
	}

	@Operation(summary = "签到奖励规则")
	@GetMapping("/configs")
	public Result<IPage<AppSignInConfigVO>> configs(Page<SignInConfig> page) {
		// 只回启用中的配置：禁用项不再是有效奖励，不能出现在 C 端规则说明里
		return Result.success(signInConfigService.page(page,
				Wrappers.<SignInConfig>lambdaQuery()
					.eq(SignInConfig::getStatus, "0")
					.orderByAsc(SignInConfig::getSortOrder)
					.orderByAsc(SignInConfig::getConsecutiveDay))
			.convert(source -> {
				AppSignInConfigVO vo = new AppSignInConfigVO();
				vo.setId(source.getId());
				vo.setConsecutiveDay(source.getConsecutiveDay());
				vo.setRewardPoint(source.getRewardPoint());
				vo.setSortOrder(source.getSortOrder());
				return vo;
			}));
	}

	@Operation(summary = "我的签到记录")
	@GetMapping("/records")
	public Result<IPage<AppSignInRecordVO>> records(Page page, String beginDate, String endDate) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(signInRecordService.getUserPage(page, userId, beginDate, endDate)
				.convert(AppSignInRecordVO::from));
	}

}
