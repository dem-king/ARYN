package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.vo.SalesCustomerClaimVO;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-待转交认领
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/claim")
@Tag(name = "待转交认领", description = "客户待首单转交认领查询")
public class SalesClaimController {

	private final ISalesCustomerClaimService salesCustomerClaimService;

	@Operation(summary = "认领分页")
	@SaCheckPermission("promotion:salescustomer:page")
	@GetMapping("/page")
	public Result<IPage<SalesCustomerClaimVO>> page(Page<SalesCustomerClaim> page, SalesCustomerClaim query) {
		return Result.success(salesCustomerClaimService.selectAdminPage(page, query));
	}

}
