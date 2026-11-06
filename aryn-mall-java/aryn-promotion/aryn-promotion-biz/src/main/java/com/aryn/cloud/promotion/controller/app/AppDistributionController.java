package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawApplyDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.vo.DistributionCenterVO;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindFacade;
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

	private final ISalesCustomerBindFacade salesCustomerBindFacade;

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

	@Operation(summary = "提现申请（已下线，佣金改为兑换优惠券）")
	@PostMapping("/withdraw/apply")
	public Result<Boolean> apply(@RequestBody(required = false) DistributionWithdrawApplyDTO dto) {
		StpUtil.isLogin();
		// 提现能力全面下线：不再产生新的提现单，保留路径只为返回明确业务错误
		throw new ArynBusinessException("提现功能已下线，佣金可兑换优惠券");
	}

	@Operation(summary = "提现进度分页")
	@GetMapping("/withdraw/page")
	public Result<IPage<DistributionWithdrawVO>> withdrawPage(Page<DistributionWithdraw> page) {
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
		// 4.1 绑定分流：邀请人为在职销售时走销售归属/待转交认领，否则保持原分销绑定
		salesCustomerBindFacade.bindFromShare(dto);
		return Result.success(Boolean.TRUE);
	}

}
