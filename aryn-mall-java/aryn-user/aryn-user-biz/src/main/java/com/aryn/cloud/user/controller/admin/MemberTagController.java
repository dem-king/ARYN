package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.MemberTag;
import com.aryn.cloud.user.service.IMemberTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员标签管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/membertag")
@Tag(description = "membertag", name = "会员标签管理")
public class MemberTagController {

	private final IMemberTagService memberTagService;

	@Operation(summary = "会员标签分页列表")
	@SaCheckPermission("user:membertag:page")
	@GetMapping("/page")
	public Result page(Page page, MemberTag memberTag) {
		return Result.success(memberTagService.getPage(page, memberTag));
	}

	@Operation(summary = "会员标签全量列表")
	@SaCheckPermission("user:membertag:get")
	@GetMapping("/list")
	public Result list() {
		return Result.success(memberTagService.list());
	}

	@Operation(summary = "会员标签查询")
	@SaCheckPermission("user:membertag:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(memberTagService.getById(id));
	}

	@SysLog("新增会员标签")
	@Operation(summary = "新增会员标签")
	@SaCheckPermission("user:membertag:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody MemberTag memberTag) {
		return Result.success(memberTagService.saveTag(memberTag));
	}

	@SysLog("修改会员标签")
	@Operation(summary = "修改会员标签")
	@SaCheckPermission("user:membertag:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody MemberTag memberTag) {
		return Result.success(memberTagService.updateTag(memberTag));
	}

	@SysLog("删除会员标签")
	@Operation(summary = "删除会员标签")
	@SaCheckPermission("user:membertag:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(memberTagService.deleteTag(id));
	}

	@SysLog("给用户打标签")
	@Operation(summary = "给用户打标签")
	@SaCheckPermission("user:membertag:edit")
	@PostMapping("/tagUser")
	public Result<Boolean> tagUser(@RequestParam String userId, @RequestBody List<String> tagIds) {
		memberTagService.tagUser(userId, tagIds);
		return Result.success(true);
	}

	@SysLog("取消用户标签")
	@Operation(summary = "取消用户标签")
	@SaCheckPermission("user:membertag:edit")
	@PostMapping("/untagUser")
	public Result<Boolean> untagUser(@RequestParam String userId, @RequestBody List<String> tagIds) {
		memberTagService.untagUser(userId, tagIds);
		return Result.success(true);
	}

	@Operation(summary = "获取用户标签列表")
	@SaCheckPermission("user:membertag:get")
	@GetMapping("/userTags")
	public Result<List<MemberTag>> userTags(@RequestParam String userId) {
		return Result.success(memberTagService.getUserTags(userId));
	}

}
