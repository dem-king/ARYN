package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.PageDesignTemplateDTO;
import com.aryn.cloud.promotion.api.vo.PageDesignTemplateVO;
import com.aryn.cloud.promotion.service.impl.PageDesignTemplateServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
	public Result<List<PageDesignTemplateVO>> list(@RequestParam(defaultValue = "2") String pageType) {
		return Result.success(templateService.listTemplates(pageType));
	}

	@SysLog("新增页面装修模板")
	@Operation(summary = "新增页面装修模板")
	@SaCheckPermission("promotion:pagedesign:template")
	@PostMapping
	public Result<PageDesignTemplateVO> add(@Valid @RequestBody PageDesignTemplateDTO template) {
		template.setId(null);
		return Result.success(templateService.saveTenantTemplate(template));
	}

	@SysLog("修改页面装修模板")
	@Operation(summary = "修改页面装修模板")
	@SaCheckPermission("promotion:pagedesign:template")
	@PutMapping("/{id}")
	public Result<PageDesignTemplateVO> edit(@PathVariable String id, @Valid @RequestBody PageDesignTemplateDTO template) {
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

	@SysLog("模板上架到市场")
	@Operation(summary = "模板上架到市场")
	@SaCheckPermission("promotion:pagedesign:template")
	@PostMapping("/{id}/market/publish")
	public Result<Boolean> publishMarket(@PathVariable String id) {
		templateService.publishMarketTemplate(id);
		return Result.success(Boolean.TRUE);
	}

	@SysLog("模板从市场下架")
	@Operation(summary = "模板从市场下架")
	@SaCheckPermission("promotion:pagedesign:template")
	@PostMapping("/{id}/market/offline")
	public Result<Boolean> offlineMarket(@PathVariable String id) {
		templateService.offlineMarketTemplate(id);
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "跨租户模板市场列表")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/market/list")
	public Result<IPage<PageDesignTemplateVO>> marketList(
			@RequestParam(defaultValue = "1") Long pageNum,
			@RequestParam(defaultValue = "10") Long pageSize,
			@RequestParam(required = false) String templateName,
			@RequestParam(required = false) String industryTag,
			@RequestParam(required = false, defaultValue = "downloadCount") String sortField) {
		return Result.success(templateService.listMarketTemplates(pageNum, pageSize, templateName, industryTag, sortField));
	}

	@Operation(summary = "跨租户市场模板详情")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/market/{id}")
	public Result<PageDesignTemplateVO> marketDetail(@PathVariable String id) {
		return Result.success(templateService.getMarketTemplate(id));
	}

	@SysLog("下载市场模板")
	@Operation(summary = "下载市场模板（复制为当前租户新模板）")
	@SaCheckPermission("promotion:pagedesign:template")
	@PostMapping("/market/{id}/download")
	public Result<String> downloadMarket(@PathVariable String id) {
		return Result.success(templateService.downloadMarketTemplate(id));
	}
}
