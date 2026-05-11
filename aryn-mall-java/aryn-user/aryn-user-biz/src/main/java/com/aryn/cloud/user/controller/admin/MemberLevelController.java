package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.vo.MemberLevelRecordVO;
import com.aryn.cloud.user.service.IMemberLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 会员等级管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/memberlevel")
@Tag(description = "memberlevel", name = "会员等级管理")
public class MemberLevelController {

	private final IMemberLevelService memberLevelService;

	@Operation(summary = "会员等级分页列表")
	@SaCheckPermission("user:memberlevel:page")
	@GetMapping("/page")
	public Result page(Page page, MemberLevel memberLevel) {
		return Result.success(memberLevelService.getPage(page, memberLevel));
	}

	@Operation(summary = "会员等级全量列表")
	@SaCheckPermission("user:memberlevel:get")
	@GetMapping("/list")
	public Result list() {
		return Result.success(memberLevelService.list());
	}

	@Operation(summary = "会员等级查询")
	@SaCheckPermission("user:memberlevel:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(memberLevelService.getDetailById(id));
	}

	@SysLog("新增会员等级")
	@Operation(summary = "新增会员等级")
	@SaCheckPermission("user:memberlevel:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody MemberLevel memberLevel) {
		return Result.success(memberLevelService.saveLevel(memberLevel));
	}

	@SysLog("修改会员等级")
	@Operation(summary = "修改会员等级")
	@SaCheckPermission("user:memberlevel:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody MemberLevel memberLevel) {
		return Result.success(memberLevelService.updateLevel(memberLevel));
	}

	@SysLog("删除会员等级")
	@Operation(summary = "删除会员等级")
	@SaCheckPermission("user:memberlevel:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(memberLevelService.deleteLevel(id));
	}

	@Operation(summary = "等级变更记录分页")
	@SaCheckPermission("user:memberlevel:page")
	@GetMapping("/record/page")
	public Result recordPage(Page page, String userId) {
		return Result.success(memberLevelService.getRecordPage(page, userId));
	}

}
