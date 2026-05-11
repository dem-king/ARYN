package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/distribution")
@Tag(name = "分销中心-API", description = "移动端分销中心接口")
public class AppDistributionController {

	private final IDistributionWithdrawService distributionWithdrawService;

	private final IDistributionCommissionFlowService distributionCommissionFlowService;

	private final IDistributionUserService distributionUserService;

	private final IDistributionConfigService distributionConfigService;

	@Operation(summary = "注册分销用户")
	@PostMapping("/register")
	public Result<DistributionUser> register(@Valid @RequestBody DistributionUserRegisterDTO dto) {
		StpUtil.isLogin();
		// 确保userId为当前登录用户
		dto.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(distributionUserService.register(dto));
	}

	@Operation(summary = "获取分销配置")
	@GetMapping("/config")
	public Result<DistributionConfig> config() {
		return Result.success(distributionConfigService.getActiveConfig());
	}

	@Operation(summary = "分销中心汇总")
	@GetMapping("/center")
	public Result<DistributionCenterVO> center() {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionWithdrawService.getCenter(userId));
	}

	@Operation(summary = "佣金流水")
	@GetMapping("/commission/page")
	public Result<IPage<DistributionCommissionFlow>> commissionPage(Page page) {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionCommissionFlowService.getUserPage(page, userId));
	}

	@Operation(summary = "提现申请")
	@PostMapping("/withdraw/apply")
	public Result<Boolean> apply(@Valid @RequestBody DistributionWithdrawApplyDTO dto) {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionWithdrawService.apply(userId, dto));
	}

	@Operation(summary = "提现进度分页")
	@GetMapping("/withdraw/page")
	public Result<IPage> withdrawPage(Page page) {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionWithdrawService.getUserPage(page, userId));
	}

	@Operation(summary = "分享参数绑定")
	@PostMapping("/share/bind")
	public Result<Boolean> shareBind(@RequestBody DistributionUserRegisterDTO dto) {
		StpUtil.isLogin();
		// 确保userId为当前登录用户
		dto.setUserId(SecurityUtils.getUser().getUserId());
		// 如果用户还不是分销用户，自动注册并绑定邀请关系
		DistributionUser existing = distributionUserService.getByUserId(dto.getUserId());
		if (existing == null) {
			distributionUserService.register(dto);
		}
		return Result.success(Boolean.TRUE);
	}

}
