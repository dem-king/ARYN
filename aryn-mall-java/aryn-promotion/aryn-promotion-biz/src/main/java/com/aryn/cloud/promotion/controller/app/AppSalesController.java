package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.vo.AppSalesCustomerVO;
import com.aryn.cloud.promotion.api.vo.AppSalesOrderVO;
import com.aryn.cloud.promotion.api.vo.AppSalesWorkbenchVO;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;
import com.aryn.cloud.promotion.service.ISalesAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序-销售工作台（只读，不提供提现入口）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/sales")
@Tag(name = "销售工作台-API", description = "小程序销售工作台、我的客户与业绩明细")
public class AppSalesController {

	private final ISalesAppService salesAppService;

	@Operation(summary = "销售工作台汇总")
	@GetMapping("/workbench")
	public Result<AppSalesWorkbenchVO> workbench() {
		StpUtil.isLogin();
		return Result.success(salesAppService.workbench(SecurityUtils.getUser().getUserId()));
	}

	@Operation(summary = "我的客户")
	@GetMapping("/customer/page")
	public Result<IPage<AppSalesCustomerVO>> customerPage(Page<SalesCustomerBind> page) {
		StpUtil.isLogin();
		return Result.success(salesAppService.customerPage(page, SecurityUtils.getUser().getUserId()));
	}

	@Operation(summary = "我的业绩明细")
	@GetMapping("/order/page")
	public Result<IPage<AppSalesOrderVO>> orderPage(Page<SalesOrder> page) {
		StpUtil.isLogin();
		return Result.success(salesAppService.orderPage(page, SecurityUtils.getUser().getUserId()));
	}

	@Operation(summary = "我的转交奖励（分销员视角）")
	@GetMapping("/transfer-reward/page")
	public Result<IPage<SalesTransferRewardVO>> transferRewardPage(Page<SalesTransferReward> page) {
		StpUtil.isLogin();
		return Result.success(
				salesAppService.transferRewardPage(page, SecurityUtils.getUser().getUserId()));
	}

}
