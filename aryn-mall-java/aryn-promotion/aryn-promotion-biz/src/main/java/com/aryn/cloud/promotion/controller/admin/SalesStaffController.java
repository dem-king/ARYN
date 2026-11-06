package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.SalesStaffResignDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffSaveDTO;
import com.aryn.cloud.promotion.api.dto.SalesStaffStatusDTO;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.vo.SalesStaffVO;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-销售员管理
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/staff")
@Tag(name = "销售员管理", description = "销售员登记、编辑、离职交接")
public class SalesStaffController {

	private final ISalesStaffService salesStaffService;

	@Operation(summary = "销售员分页")
	@SaCheckPermission("promotion:salesstaff:page")
	@GetMapping("/page")
	public Result<IPage<SalesStaffVO>> page(Page<SalesStaff> page, SalesStaff query) {
		return Result.success(salesStaffService.selectAdminPage(page, query));
	}

	@Operation(summary = "销售员详情")
	@SaCheckPermission("promotion:salesstaff:get")
	@GetMapping("/{id}")
	public Result<SalesStaffVO> get(@PathVariable String id) {
		return Result.success(salesStaffService.getDetail(id));
	}

	@Operation(summary = "在职销售下拉（客户改归属/离职接收人/业绩单筛选用）")
	@SaCheckPermission("promotion:salescustomer:page")
	@GetMapping("/options")
	public Result<List<SalesStaffVO>> options(@RequestParam(required = false) String keyword) {
		return Result.success(salesStaffService.options(keyword));
	}

	@SysLog("新增销售员")
	@Operation(summary = "新增销售员")
	@SaCheckPermission("promotion:salesstaff:add")
	@PostMapping("/add")
	public Result<Boolean> add(@Valid @RequestBody SalesStaffSaveDTO dto) {
		return Result.success(salesStaffService.addStaff(dto));
	}

	@SysLog("编辑销售员")
	@Operation(summary = "编辑销售员")
	@SaCheckPermission("promotion:salesstaff:edit")
	@PutMapping("/edit")
	public Result<Boolean> edit(@Valid @RequestBody SalesStaffSaveDTO dto) {
		return Result.success(salesStaffService.editStaff(dto));
	}

	@SysLog("销售离职交接")
	@Operation(summary = "销售离职交接")
	@SaCheckPermission("promotion:salesstaff:edit")
	@PostMapping("/resign")
	public Result<Boolean> resign(@Valid @RequestBody SalesStaffResignDTO dto) {
		return Result.success(salesStaffService.resign(dto));
	}

	@SysLog("销售状态变更")
	@Operation(summary = "销售状态变更")
	@SaCheckPermission("promotion:salesstaff:edit")
	@PostMapping("/status")
	public Result<Boolean> status(@Valid @RequestBody SalesStaffStatusDTO dto) {
		return Result.success(salesStaffService.changeStatus(dto));
	}

}
