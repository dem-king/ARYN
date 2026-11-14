package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.entity.EnterpriseLevel;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业会员等级管理（双轨会员企业轨）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/enterpriselevel")
@Tag(description = "enterpriselevel", name = "企业会员等级")
public class EnterpriseLevelController {

	private final IEnterpriseMemberService enterpriseMemberService;

	@Operation(summary = "企业等级列表")
	@SaCheckPermission("user:enterpriselevel:page")
	@GetMapping("/list")
	public Result<List<EnterpriseLevel>> list(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {
		return Result.success(enterpriseMemberService.listLevels(
				ArynTenantContextHolder.getTenantId(), status, keyword));
	}

	@SysLog("新增企业等级")
	@Operation(summary = "新增企业等级")
	@SaCheckPermission("user:enterpriselevel:save")
	@PostMapping
	public Result<EnterpriseLevel> save(@RequestBody EnterpriseLevel level) {
		return Result.success(enterpriseMemberService.saveLevel(
				ArynTenantContextHolder.getTenantId(), level));
	}

	@SysLog("修改企业等级")
	@Operation(summary = "修改企业等级")
	@SaCheckPermission("user:enterpriselevel:save")
	@PutMapping("/{id}")
	public Result<EnterpriseLevel> update(@PathVariable String id, @RequestBody EnterpriseLevel level) {
		return Result.success(enterpriseMemberService.updateLevel(
				ArynTenantContextHolder.getTenantId(), id, level));
	}

	@SysLog("删除企业等级")
	@Operation(summary = "删除企业等级（挂载主体回到未定级）")
	@SaCheckPermission("user:enterpriselevel:del")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		enterpriseMemberService.deleteLevel(ArynTenantContextHolder.getTenantId(), id);
		return Result.success();
	}

}
