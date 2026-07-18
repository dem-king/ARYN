package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.MemberBenefit;
import com.aryn.cloud.user.service.IMemberBenefitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员权益管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/memberbenefit")
@Tag(description = "memberbenefit", name = "会员权益管理")
public class MemberBenefitController {

	private final IMemberBenefitService memberBenefitService;

	@Operation(summary = "会员权益分页列表")
	@SaCheckPermission("user:memberbenefit:page")
	@GetMapping("/page")
	public Result page(Page page, MemberBenefit memberBenefit) {
		return Result.success(memberBenefitService.getPage(page, memberBenefit));
	}

	@Operation(summary = "会员权益查询")
	@SaCheckPermission("user:memberbenefit:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(memberBenefitService.getBenefitDetail(id));
	}

	@SysLog("新增会员权益")
	@Operation(summary = "新增会员权益")
	@SaCheckPermission("user:memberbenefit:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody MemberBenefit memberBenefit) {
		return Result.success(memberBenefitService.saveBenefit(memberBenefit));
	}

	@SysLog("修改会员权益")
	@Operation(summary = "修改会员权益")
	@SaCheckPermission("user:memberbenefit:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody MemberBenefit memberBenefit) {
		return Result.success(memberBenefitService.updateBenefit(memberBenefit));
	}

	@SysLog("删除会员权益")
	@Operation(summary = "删除会员权益")
	@SaCheckPermission("user:memberbenefit:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(memberBenefitService.deleteBenefit(id));
	}

	@SysLog("绑定权益等级")
	@Operation(summary = "绑定权益等级")
	@SaCheckPermission("user:memberbenefit:edit")
	@PostMapping("/bindLevels")
	public Result<Boolean> bindLevels(@RequestParam String benefitId, @RequestBody List<String> levelIds) {
		memberBenefitService.bindLevels(benefitId, levelIds);
		return Result.success(true);
	}

	@Operation(summary = "根据等级查询权益列表")
	@SaCheckPermission("user:memberbenefit:get")
	@GetMapping("/levelBenefits")
	public Result<List<MemberBenefit>> levelBenefits(@RequestParam String levelId) {
		return Result.success(memberBenefitService.getLevelBenefits(levelId));
	}

}
