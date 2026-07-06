package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.api.entity.MemberPaidOrder;
import com.aryn.cloud.user.service.IMemberPaidOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 付费会员订单管理
 *
 * @author aryn
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/memberpaidorder")
@Tag(description = "memberpaidorder", name = "付费会员订单管理")
public class MemberPaidOrderController {

	private final IMemberPaidOrderService memberPaidOrderService;

	@Operation(summary = "付费会员订单分页列表")
	@SaCheckPermission("user:memberpaidorder:page")
	@GetMapping("/page")
	public Result page(Page page, MemberPaidOrder memberPaidOrder) {
		return Result.success(memberPaidOrderService.getPage(page, memberPaidOrder));
	}

}