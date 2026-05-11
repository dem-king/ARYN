package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.service.IGroupBuyActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/groupbuy/activity")
@Tag(description = "groupbuy-activity", name = "拼团活动管理")
public class GroupBuyActivityController {

	private final IGroupBuyActivityService groupBuyActivityService;

	@Operation(summary = "拼团活动列表")
	@SaCheckPermission("promotion:groupbuy:page")
	@GetMapping("/page")
	public Result page(Page page, GroupBuyActivity activity) {
		return Result.success(groupBuyActivityService.getAdminPage(page, activity));
	}

	@Operation(summary = "拼团活动查询")
	@SaCheckPermission("promotion:groupbuy:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(groupBuyActivityService.getDetail(id));
	}

	@SysLog("新增拼团活动")
	@Operation(summary = "新增拼团活动")
	@SaCheckPermission("promotion:groupbuy:add")
	@PostMapping
	public Result add(@Valid @RequestBody GroupBuyActivity activity) {
		return Result.success(groupBuyActivityService.save(activity));
	}

	@SysLog("修改拼团活动")
	@Operation(summary = "修改拼团活动")
	@SaCheckPermission("promotion:groupbuy:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody GroupBuyActivity activity) {
		return Result.success(groupBuyActivityService.updateById(activity));
	}

	@SysLog("删除拼团活动")
	@Operation(summary = "删除拼团活动")
	@SaCheckPermission("promotion:groupbuy:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		return Result.success(groupBuyActivityService.removeById(id));
	}
}
