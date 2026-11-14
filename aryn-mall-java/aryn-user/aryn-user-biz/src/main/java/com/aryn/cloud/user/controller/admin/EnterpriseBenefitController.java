package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.dto.EnterpriseBenefitSaveDTO;
import com.aryn.cloud.user.api.entity.EnterpriseBenefit;
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
 * 企业会员权益管理（双轨会员企业轨）。
 *
 * <p>权益类型：1企业折扣率 2免邮 3协议价政策池 4开票资质 5专属客服 6返利率(预留) 7账期天数(预留)。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/enterprisebenefit")
@Tag(description = "enterprisebenefit", name = "企业会员权益")
public class EnterpriseBenefitController {

	private final IEnterpriseMemberService enterpriseMemberService;

	@Operation(summary = "企业权益列表")
	@SaCheckPermission("user:enterprisebenefit:page")
	@GetMapping("/list")
	public Result<List<EnterpriseBenefit>> list(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {
		return Result.success(enterpriseMemberService.listBenefits(
				ArynTenantContextHolder.getTenantId(), status, keyword));
	}

	@Operation(summary = "权益挂载的企业等级ID集合（编辑回显）")
	@SaCheckPermission("user:enterprisebenefit:page")
	@GetMapping("/{id}/level-rels")
	public Result<List<String>> levelRels(@PathVariable String id) {
		return Result.success(enterpriseMemberService.listLevelIdsOfBenefit(
				ArynTenantContextHolder.getTenantId(), id));
	}

	@SysLog("新增企业权益")
	@Operation(summary = "新增企业权益（含挂载等级）")
	@SaCheckPermission("user:enterprisebenefit:save")
	@PostMapping
	public Result<EnterpriseBenefit> save(@RequestBody EnterpriseBenefitSaveDTO dto) {
		return Result.success(enterpriseMemberService.saveBenefit(
				ArynTenantContextHolder.getTenantId(), dto.getBenefit(), dto.getLevelIds()));
	}

	@SysLog("修改企业权益")
	@Operation(summary = "修改企业权益（含挂载等级）")
	@SaCheckPermission("user:enterprisebenefit:save")
	@PutMapping("/{id}")
	public Result<EnterpriseBenefit> update(@PathVariable String id, @RequestBody EnterpriseBenefitSaveDTO dto) {
		return Result.success(enterpriseMemberService.updateBenefit(
				ArynTenantContextHolder.getTenantId(), id, dto.getBenefit(), dto.getLevelIds()));
	}

	@SysLog("删除企业权益")
	@Operation(summary = "删除企业权益")
	@SaCheckPermission("user:enterprisebenefit:del")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		enterpriseMemberService.deleteBenefit(ArynTenantContextHolder.getTenantId(), id);
		return Result.success();
	}

}
