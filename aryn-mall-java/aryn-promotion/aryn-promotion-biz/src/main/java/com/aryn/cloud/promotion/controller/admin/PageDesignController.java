/** 
  * Copyright (c) 2025 天启雨数科技有限公司 
  * All rights reserved. 
  * <p> 
  * 注意： 
  * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。 
 
  */
package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.dto.PageDesignReleaseAuditDTO;
import com.aryn.cloud.promotion.api.dto.PageDesignReleaseCreateDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignMetricDaily;
import com.aryn.cloud.promotion.api.vo.PageDesignAuditLogVO;
import com.aryn.cloud.promotion.api.vo.PageDesignAssetVO;
import com.aryn.cloud.promotion.api.vo.PageDesignDiffVO;
import com.aryn.cloud.promotion.api.vo.PageDesignEditorVO;
import com.aryn.cloud.promotion.api.dto.PageDesignPublishDTO;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignReleaseVO;
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.api.vo.PageDesignVersionVO;
import com.aryn.cloud.promotion.service.IPageDesignService;
import com.aryn.cloud.promotion.service.IPageDesignVersionService;
import com.aryn.cloud.promotion.service.PageDesignAssetService;
import com.aryn.cloud.promotion.service.PageDesignAuditService;
import com.aryn.cloud.promotion.service.PageDesignDiffService;
import com.aryn.cloud.promotion.service.PageDesignMetricService;
import com.aryn.cloud.promotion.service.PageDesignPreviewService;
import com.aryn.cloud.promotion.service.PageDesignReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 页面设计
 *
 * @author 雨滴kian
 * @date 2022/12/07
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/pagedesign")
@Tag(description = "pagedesign", name = "页面设计")
public class PageDesignController {

	private final IPageDesignService pageDesignService;

	private final IPageDesignVersionService pageDesignVersionService;

	private final PageDesignPreviewService pageDesignPreviewService;

	private final PageDesignReleaseService pageDesignReleaseService;

	private final PageDesignAuditService pageDesignAuditService;

	private final PageDesignDiffService pageDesignDiffService;

	private final PageDesignAssetService pageDesignAssetService;

	private final PageDesignMetricService pageDesignMetricService;

	@Operation(summary = "页面设计列表")
	@SaCheckPermission("promotion:pagedesign:page")
	@GetMapping("/page")
	public Result<IPage<PageDesign>> page(Page page, PageDesign pageDesign) {
		IPage<PageDesign> iPage = pageDesignService.page(page, Wrappers.query(pageDesign));
		return Result.success(iPage);
	}

	@Operation(summary = "页面设计查询")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/{id}")
	public Result<PageDesign> getById(@PathVariable String id) {
		return Result.success(pageDesignService.getById(id));
	}

	@SysLog("新增页面设计")
	@Operation(summary = "页面设计新增")
	@SaCheckPermission("promotion:pagedesign:add")
	@PostMapping
	public Result<String> add(@RequestBody PageDesign pageDesign) {
		pageDesignService.save(pageDesign);
		return Result.success(pageDesign.getId());
	}

	@SysLog("修改页面设计")
	@Operation(summary = "页面设计修改")
	@SaCheckPermission("promotion:pagedesign:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody PageDesign pageDesign) {
		return Result.success(pageDesignService.updatePageDesignById(pageDesign));
	}

	@SysLog("保存页面装修草稿")
	@Operation(summary = "保存页面装修草稿")
	@SaCheckPermission("promotion:pagedesign:edit")
	@PutMapping("/{id}/draft")
	public Result<Long> saveDraft(@PathVariable String id, @Valid @RequestBody PageDesignDraftDTO draft) {
		draft.setId(id);
		return Result.success(pageDesignService.saveDraft(draft));
	}

	@Operation(summary = "获取页面装修编辑数据")
	@SaCheckPermission("promotion:pagedesign:edit")
	@GetMapping("/{id}/editor")
	public Result<PageDesignEditorVO> getEditor(@PathVariable String id) {
		return Result.success(pageDesignService.getEditor(id));
	}

	@Operation(summary = "Create a short-lived page preview token")
	@SaCheckPermission("promotion:pagedesign:edit")
	@PostMapping("/{id}/preview-token")
	public Result<String> createPreviewToken(@PathVariable String id, @RequestParam Long draftRevision) {
		return Result.success(pageDesignPreviewService.createPreviewToken(id, draftRevision));
	}

	@SysLog("复制页面装修")
	@Operation(summary = "复制页面装修")
	@SaCheckPermission("promotion:pagedesign:add")
	@PostMapping("/{id}/copy")
	public Result<PageDesign> copy(@PathVariable String id) {
		return Result.success(pageDesignService.copyPage(id));
	}

	@SysLog("发布页面装修")
	@Operation(summary = "发布页面装修")
	@SaCheckPermission("promotion:pagedesign:publish")
	@PostMapping("/{id}/publish")
	public Result<PageDesignVersion> publish(@PathVariable String id,
			@Valid @RequestBody PageDesignPublishDTO request) {
		request.setId(id);
		return Result.success(pageDesignVersionService.publish(request));
	}

	@Operation(summary = "发布前校验当前草稿")
	@SaCheckPermission("promotion:pagedesign:edit")
	@PostMapping("/{id}/validate")
	public Result<PageDesignValidationVO> validate(@PathVariable String id) {
		return Result.success(pageDesignReleaseService.validateDraft(id));
	}

	@SysLog("提交页面装修发布申请")
	@Operation(summary = "提交页面装修发布申请")
	@SaCheckPermission("promotion:pagedesign:submit")
	@PostMapping("/{id}/release")
	public Result<PageDesignReleaseVO> submitRelease(@PathVariable String id,
			@Valid @RequestBody PageDesignReleaseCreateDTO request) {
		return Result.success(pageDesignReleaseService.submit(id, request));
	}

	@Operation(summary = "页面装修发布申请列表")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/{id}/releases")
	public Result<List<PageDesignReleaseVO>> releases(@PathVariable String id) {
		return Result.success(pageDesignReleaseService.listReleases(id));
	}

	@SysLog("审批页面装修发布申请")
	@Operation(summary = "审批页面装修发布申请")
	@SaCheckPermission("promotion:pagedesign:approve")
	@PostMapping("/releases/{releaseId}/approve")
	public Result<PageDesignReleaseVO> approveRelease(@PathVariable String releaseId,
			@Valid @RequestBody PageDesignReleaseAuditDTO request) {
		return Result.success(pageDesignReleaseService.audit(releaseId, request));
	}

	@SysLog("取消页面装修发布申请")
	@Operation(summary = "取消页面装修发布申请")
	@SaCheckPermission("promotion:pagedesign:submit")
	@PostMapping("/{id}/releases/{releaseId}/cancel")
	public Result<PageDesignReleaseVO> cancelRelease(@PathVariable String id, @PathVariable String releaseId) {
		return Result.success(pageDesignReleaseService.cancel(id, releaseId));
	}

	@Operation(summary = "页面装修版本差异")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/{id}/versions/{fromVersionId}/diff/{toVersionId}")
	public Result<PageDesignDiffVO> versionDiff(@PathVariable String id, @PathVariable String fromVersionId,
			@PathVariable String toVersionId) {
		return Result.success(pageDesignDiffService.diff(id, fromVersionId, toVersionId));
	}

	@Operation(summary = "页面装修审计日志")
	@SaCheckPermission("promotion:pagedesign:audit")
	@GetMapping("/{id}/audit-logs")
	public Result<List<PageDesignAuditLogVO>> auditLogs(@PathVariable String id) {
		return Result.success(pageDesignAuditService.listLogs(id));
	}

	@Operation(summary = "页面装修素材引用检查")
	@SaCheckPermission("promotion:pagedesign:edit")
	@GetMapping("/{id}/assets")
	public Result<PageDesignAssetVO> assets(@PathVariable String id) {
		return Result.success(pageDesignAssetService.check(id));
	}

	@Operation(summary = "页面装修聚合指标")
	@SaCheckPermission("promotion:pagedesign:metrics")
	@GetMapping("/{id}/metrics")
	public Result<List<PageDesignMetricDaily>> metrics(@PathVariable String id,
			@RequestParam(defaultValue = "7") int days) {
		return Result.success(pageDesignMetricService.listMetrics(id, days));
	}

	@SysLog("下线页面装修")
	@Operation(summary = "下线页面装修")
	@SaCheckPermission("promotion:pagedesign:publish")
	@PostMapping("/{id}/unpublish")
	public Result<Boolean> unpublish(@PathVariable String id) {
		return Result.success(pageDesignVersionService.unpublish(id));
	}

	@Operation(summary = "页面装修历史版本")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/{id}/versions")
	public Result<List<PageDesignVersionVO>> versions(@PathVariable String id) {
		return Result.success(pageDesignVersionService.listVersions(id));
	}

	@SysLog("回滚页面装修")
	@Operation(summary = "回滚页面装修")
	@SaCheckPermission("promotion:pagedesign:rollback")
	@PostMapping("/{id}/versions/{versionId}/rollback")
	public Result<PageDesignVersion> rollback(@PathVariable String id, @PathVariable String versionId,
			@RequestBody(required = false) PageDesignPublishDTO request) {
		String remark = request == null ? null : request.getPublishRemark();
		return Result.success(pageDesignVersionService.rollback(id, versionId, remark));
	}

	@SysLog("删除页面设计")
	@Operation(summary = "页面设计删除")
	@SaCheckPermission("promotion:pagedesign:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(pageDesignService.removeById(id));
	}

	@Operation(summary = "获取首页装修数据")
	@SaCheckPermission("promotion:pagedesign:edit")
	@GetMapping("/home-edit")
	public Result<PageDesign> getHomeEdit() {
		return Result.success(pageDesignService.getOrCreateHomePage());
	}

	@Operation(summary = "页面设计查询")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping("/home")
	public Result<PageDesign> getHomePage(PageDesign request) {
		return Result.success(pageDesignService.getOne(
				Wrappers.query(request).lambda().eq(PageDesign::getHomeStatus, CommonConstants.YES).last("limit 1")));
	}

}
