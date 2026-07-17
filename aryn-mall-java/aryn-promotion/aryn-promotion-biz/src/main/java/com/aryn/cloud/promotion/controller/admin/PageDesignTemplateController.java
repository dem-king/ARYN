package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.service.impl.PageDesignTemplateServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pagedesign/templates")
public class PageDesignTemplateController {

	private final PageDesignTemplateServiceImpl templateService;

	@Operation(summary = "页面装修模板列表")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping
	public Result<List<PageDesignTemplate>> list(@RequestParam(defaultValue = "2") String pageType) {
		return Result.success(templateService.listTemplates(pageType));
	}

	@SysLog("新增页面装修模板")
	@Operation(summary = "新增页面装修模板")
	@SaCheckPermission("promotion:pagedesign:template")
	@PostMapping
	public Result<PageDesignTemplate> add(@RequestBody PageDesignTemplate template) {
		template.setId(null);
		return Result.success(templateService.saveTenantTemplate(template));
	}

	@SysLog("修改页面装修模板")
	@Operation(summary = "修改页面装修模板")
	@SaCheckPermission("promotion:pagedesign:template")
	@PutMapping("/{id}")
	public Result<PageDesignTemplate> edit(@PathVariable String id, @RequestBody PageDesignTemplate template) {
		template.setId(id);
		return Result.success(templateService.saveTenantTemplate(template));
	}

	@SysLog("删除页面装修模板")
	@Operation(summary = "删除页面装修模板")
	@SaCheckPermission("promotion:pagedesign:template")
	@DeleteMapping("/{id}")
	public Result<Boolean> remove(@PathVariable String id) {
		return Result.success(templateService.removeTenantTemplate(id));
	}
}
