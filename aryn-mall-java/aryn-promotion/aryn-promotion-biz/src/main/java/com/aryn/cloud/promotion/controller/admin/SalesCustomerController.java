package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.SalesCustomerAssignDTO;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBindLog;
import com.aryn.cloud.promotion.api.vo.SalesCustomerBindLogVO;
import com.aryn.cloud.promotion.api.vo.SalesCustomerVO;
import com.aryn.cloud.promotion.service.ISalesCustomerBindLogService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-客户归属
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/customer")
@Tag(name = "客户归属", description = "客户销售归属查询、手动指派、换绑日志")
public class SalesCustomerController {

	private final ISalesCustomerBindService salesCustomerBindService;

	private final ISalesCustomerBindLogService salesCustomerBindLogService;

	@Operation(summary = "客户归属分页")
	@SaCheckPermission("promotion:salescustomer:page")
	@GetMapping("/page")
	public Result<IPage<SalesCustomerVO>> page(Page<SalesCustomerBind> page, SalesCustomerBind query) {
		return Result.success(salesCustomerBindService.selectAdminPage(page, query));
	}

	@SysLog("后台改绑客户归属")
	@Operation(summary = "手动指派/改归属")
	@SaCheckPermission("promotion:salescustomer:edit")
	@PostMapping("/assign")
	public Result<Boolean> assign(@Valid @RequestBody SalesCustomerAssignDTO dto) {
		return Result.success(salesCustomerBindService.assign(dto));
	}

	@Operation(summary = "换绑日志分页")
	@SaCheckPermission("promotion:salescustomer:page")
	@GetMapping("/bind-log/page")
	public Result<IPage<SalesCustomerBindLogVO>> bindLogPage(Page<SalesCustomerBindLog> page,
			@RequestParam(required = false) String customerUserId) {
		IPage<SalesCustomerBindLog> result = salesCustomerBindLogService.selectAdminPage(page, customerUserId);
		return Result.success(result.convert(SalesCustomerController::toBindLogVO));
	}

	private static SalesCustomerBindLogVO toBindLogVO(SalesCustomerBindLog bindLog) {
		if (bindLog == null) {
			return null;
		}
		SalesCustomerBindLogVO vo = new SalesCustomerBindLogVO();
		BeanUtils.copyProperties(bindLog, vo);
		return vo;
	}

}
