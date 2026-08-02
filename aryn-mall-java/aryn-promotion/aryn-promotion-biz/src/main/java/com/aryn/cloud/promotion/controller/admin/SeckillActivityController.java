package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.SeckillActivityDTO;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
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
@RequestMapping("/seckill/activity")
@Tag(description = "seckill-activity", name = "秒杀活动管理")
public class SeckillActivityController {

	private final ISeckillActivityService seckillActivityService;

	@Operation(summary = "秒杀活动列表")
	@SaCheckPermission("promotion:seckill:page")
	@GetMapping("/page")
	public Result page(Page page, SeckillActivity activity) {
		return Result.success(seckillActivityService.getAdminPage(page, activity));
	}

	@Operation(summary = "秒杀活动详情")
	@SaCheckPermission("promotion:seckill:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(seckillActivityService.getDetail(id));
	}

	@SysLog("新增秒杀活动")
	@Operation(summary = "新增秒杀活动")
	@SaCheckPermission("promotion:seckill:add")
	@PostMapping
	public Result add(@Valid @RequestBody SeckillActivityDTO dto) {
		return Result.success(seckillActivityService.saveWithSessions(dto));
	}

	@SysLog("修改秒杀活动")
	@Operation(summary = "修改秒杀活动")
	@SaCheckPermission("promotion:seckill:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody SeckillActivityDTO dto) {
		return Result.success(seckillActivityService.updateWithSessions(dto));
	}

	@SysLog("删除秒杀活动")
	@Operation(summary = "删除秒杀活动")
	@SaCheckPermission("promotion:seckill:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		SeckillActivity activity = seckillActivityService.getById(id);
		if (activity == null) {
			return Result.fail("活动不存在");
		}
		if (activity.getStatus() != null && activity.getStatus() == 1) {
			return Result.fail("进行中的活动不能删除");
		}
		return Result.success(seckillActivityService.removeById(id));
	}

	@SysLog("更新秒杀活动状态")
	@Operation(summary = "更新秒杀活动状态")
	@SaCheckPermission("promotion:seckill:status")
	@PutMapping("/{id}/status")
	public Result updateStatus(@PathVariable("id") String id, @RequestParam("status") Integer status) {
		if (status == null || !VALID_STATUS.contains(status)) {
			return Result.fail("非法的活动状态值");
		}
		return Result.success(seckillActivityService.updateStatus(id, status));
	}

	private static final Set<Integer> VALID_STATUS = Set.of(0, 1, 2, 3);
}