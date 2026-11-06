package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;
import com.aryn.cloud.promotion.service.ISalesTransferRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-客户转交奖励
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/transfer-reward")
@Tag(name = "客户转交奖励", description = "分销员转交奖励查询")
public class SalesTransferRewardController {

	private final ISalesTransferRewardService salesTransferRewardService;

	@Operation(summary = "转交奖励分页")
	@SaCheckPermission("promotion:salescustomer:page")
	@GetMapping("/page")
	public Result<IPage<SalesTransferRewardVO>> page(Page<SalesTransferReward> page,
			SalesTransferReward query) {
		return Result.success(salesTransferRewardService.selectAdminPage(page, query));
	}

}
