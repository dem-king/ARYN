
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.upms.api.entity.SysLogisticsCompany;
import com.aryn.cloud.upms.service.ISysLogisticsCompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物流公司
 *
 * @author 雨滴kian
 * @date 2023/1/10
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/logisticscompany")
@Tag(description = "logisticscompany", name = "物流公司")
public class SysLogisticsCompanyController {

	private final ISysLogisticsCompanyService logisticsCompanyService;

	@Operation(summary = "物流公司列表")
	@SaCheckPermission("upms:logisticscompany:page")
	@GetMapping("/page")
	public Result<IPage<SysLogisticsCompany>> page(Page page, SysLogisticsCompany logisticsCompany) {
		IPage<SysLogisticsCompany> iPage = logisticsCompanyService.page(page, Wrappers.query(logisticsCompany));
		return Result.success(iPage);
	}

	@Operation(summary = "查询全部物流公司")
	@GetMapping("/list")
	public Result<List<SysLogisticsCompany>> getList(SysLogisticsCompany logisticsCompany) {
		return Result.success(logisticsCompanyService.list(Wrappers.query(logisticsCompany)));
	}

	@Operation(summary = "物流公司查询")
	@SaCheckPermission("upms:logisticscompany:get")
	@GetMapping("/{id}")
	public Result<SysLogisticsCompany> getById(@PathVariable("id") String id) {
		return Result.success(logisticsCompanyService.getById(id));
	}

	@SysLog("新增物流公司")
	@Operation(summary = "物流公司新增")
	@SaCheckPermission("upms:logisticscompany:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SysLogisticsCompany logisticsCompany) {
		return Result.success(logisticsCompanyService.save(logisticsCompany));
	}

	@SysLog("修改物流公司")
	@Operation(summary = "物流公司修改")
	@SaCheckPermission("upms:logisticscompany:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SysLogisticsCompany logisticsCompany) {
		return Result.success(logisticsCompanyService.updateById(logisticsCompany));
	}

	@SysLog("删除物流公司")
	@Operation(summary = "物流公司删除")
	@SaCheckPermission("upms:logisticscompany:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(logisticsCompanyService.removeById(id));
	}

}
