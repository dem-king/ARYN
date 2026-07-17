package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.DistributionWithdrawAuditDTO;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import com.aryn.cloud.promotion.api.vo.DistributionWithdrawVO;
import com.aryn.cloud.promotion.service.IDistributionWithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/withdraw")
@Tag(name = "分销提现", description = "分销提现管理")
public class DistributionWithdrawController {

	private final IDistributionWithdrawService distributionWithdrawService;

	@Operation(summary = "提现分页")
	@SaCheckPermission("promotion:distributionwithdraw:page")
	@GetMapping("/page")
	public Result<IPage<DistributionWithdrawVO>> page(Page<DistributionWithdraw> page, DistributionWithdraw query) {
		return Result.success(distributionWithdrawService.getAdminPage(page, query));
	}

	@Operation(summary = "提现详情")
	@SaCheckPermission("promotion:distributionwithdraw:get")
	@GetMapping("/{id}")
	public Result<DistributionWithdrawVO> get(@PathVariable String id) {
		return Result.success(distributionWithdrawService.getDetail(id));
	}

	@SysLog("提现审核")
	@Operation(summary = "提现审核")
	@SaCheckPermission("promotion:distributionwithdraw:audit")
	@PostMapping("/audit")
	public Result<Boolean> audit(@Valid @RequestBody DistributionWithdrawAuditDTO dto) {
		return Result.success(distributionWithdrawService.audit(dto));
	}

	@SysLog("历史提现账号重加密")
	@Operation(summary = "历史提现账号重加密")
	@SaCheckPermission("promotion:distributionwithdraw:audit")
	@PostMapping("/reencrypt-legacy-accounts")
	public Result<Integer> reencryptLegacyAccounts() {
		return Result.success(distributionWithdrawService.reencryptLegacyAccounts());
	}

}
