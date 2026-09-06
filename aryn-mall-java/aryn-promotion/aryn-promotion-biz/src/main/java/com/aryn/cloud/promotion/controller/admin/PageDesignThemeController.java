package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.entity.PageDesignTheme;
import com.aryn.cloud.promotion.service.IPageDesignThemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 页面装修主题
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/pagedesign/themes")
@Tag(description = "pagedesign-themes", name = "页面装修主题")
public class PageDesignThemeController {

	private final IPageDesignThemeService pageDesignThemeService;

	@Operation(summary = "主题列表")
	@SaCheckPermission("promotion:pagedesign:get")
	@GetMapping
	public Result<List<PageDesignTheme>> list() {
		return Result.success(pageDesignThemeService.listThemes());
	}

	@SysLog("新增页面装修主题")
	@Operation(summary = "新增主题")
	@SaCheckPermission("promotion:pagedesign:theme")
	@PostMapping
	public Result<PageDesignTheme> add(@RequestBody PageDesignTheme theme) {
		return Result.success(pageDesignThemeService.createTheme(theme));
	}

	@SysLog("修改页面装修主题")
	@Operation(summary = "修改主题")
	@SaCheckPermission("promotion:pagedesign:theme")
	@PutMapping("/{id}")
	public Result<Boolean> edit(@PathVariable String id, @RequestBody PageDesignTheme theme) {
		theme.setId(id);
		return Result.success(pageDesignThemeService.updateTheme(theme));
	}

	@SysLog("删除页面装修主题")
	@Operation(summary = "删除主题")
	@SaCheckPermission("promotion:pagedesign:theme")
	@DeleteMapping("/{id}")
	public Result<Boolean> delete(@PathVariable String id) {
		return Result.success(pageDesignThemeService.deleteTheme(id));
	}

}
