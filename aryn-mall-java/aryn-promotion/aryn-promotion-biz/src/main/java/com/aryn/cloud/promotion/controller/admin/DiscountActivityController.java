package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/discount/activity")
@Tag(description = "discount-activity", name = "折扣活动管理")
public class DiscountActivityController {

	private final IDiscountActivityService discountActivityService;

	@Operation(summary = "折扣活动列表")
	@SaCheckPermission("promotion:discount:page")
	@GetMapping("/page")
	public Result page(Page page, DiscountActivity activity) {
		return Result.success(discountActivityService.getAdminPage(page, activity));
	}

	@Operation(summary = "折扣活动详情")
	@SaCheckPermission("promotion:discount:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(discountActivityService.getDetail(id));
	}

	@SysLog("新增折扣活动")
	@Operation(summary = "新增折扣活动")
	@SaCheckPermission("promotion:discount:add")
	@PostMapping
	public Result add(@Valid @RequestBody DiscountActivityDTO dto) {
		return Result.success(discountActivityService.saveWithGoods(dto));
	}

	@SysLog("修改折扣活动")
	@Operation(summary = "修改折扣活动")
	@SaCheckPermission("promotion:discount:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody DiscountActivityDTO dto) {
		return Result.success(discountActivityService.updateWithGoods(dto));
	}

	@SysLog("删除折扣活动")
	@Operation(summary = "删除折扣活动")
	@SaCheckPermission("promotion:discount:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		DiscountActivity activity = discountActivityService.getById(id);
		if (activity == null) {
			return Result.fail("活动不存在");
		}
		if (activity.getStatus() != null && activity.getStatus() == 1) {
			return Result.fail("进行中的活动不能删除");
		}
		return Result.success(discountActivityService.removeById(id));
	}

	@SysLog("更新折扣活动状态")
	@Operation(summary = "更新折扣活动状态")
	@SaCheckPermission("promotion:discount:status")
	@PutMapping("/{id}/status")
	public Result updateStatus(@PathVariable("id") String id, @RequestParam("status") Integer status) {
		if (status == null || !VALID_STATUS.contains(status)) {
			return Result.fail("非法的活动状态值");
		}
		return Result.success(discountActivityService.updateStatus(id, status));
	}

	private static final Set<Integer> VALID_STATUS = Set.of(0, 1, 2, 3);
}