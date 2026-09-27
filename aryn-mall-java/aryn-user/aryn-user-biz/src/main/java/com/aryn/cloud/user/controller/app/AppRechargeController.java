package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.api.vo.AppRechargeConfigVO;
import com.aryn.cloud.user.service.IRechargeConfigService;
import com.aryn.cloud.user.service.IRechargeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端充值
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/recharge")
@Tag(name = "充值-API")
public class AppRechargeController {

	private final IRechargeConfigService rechargeConfigService;

	private final IRechargeOrderService rechargeOrderService;

	@Operation(summary = "获取充值配置列表")
	@GetMapping("/config/list")
	public Result<List<AppRechargeConfigVO>> configList() {
		// 只回展示字段：储值配置实体带 createBy/tenantId/delFlag 等内部字段
		return Result.success(rechargeConfigService.list(
						Wrappers.<RechargeConfig>lambdaQuery().eq(RechargeConfig::getStatus, "0").orderByAsc(RechargeConfig::getSortOrder))
				.stream().map(source -> {
					AppRechargeConfigVO vo = new AppRechargeConfigVO();
					vo.setId(source.getId());
					vo.setRechargeAmount(source.getRechargeAmount());
					vo.setGiftAmount(source.getGiftAmount());
					vo.setGiftPoint(source.getGiftPoint());
					vo.setSortOrder(source.getSortOrder());
					return vo;
				}).toList());
	}

	@Operation(summary = "创建充值订单")
	@PostMapping("/order")
	public Result<RechargeOrder> createOrder(@RequestParam String rechargeConfigId) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(rechargeOrderService.createOrder(userId, rechargeConfigId));
	}

	@Operation(summary = "我的充值订单")
	@GetMapping("/order/page")
	public Result orderPage(Page page) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(rechargeOrderService.page(page,
				Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getUserId, userId).orderByDesc(RechargeOrder::getCreateTime)));
	}

}
