package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.ClearanceRule;
import com.aryn.cloud.promotion.service.impl.ClearanceRuleService;
import com.aryn.cloud.promotion.service.impl.MallZoneActivitySyncServiceImpl;
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
 * 出清时间分层规则管理：距到期越近再打折越深。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/clearance-rule")
@Tag(description = "clearance-rule", name = "出清折扣规则")
public class ClearanceRuleController {

	private final ClearanceRuleService clearanceRuleService;

	private final MallZoneActivitySyncServiceImpl mallZoneActivitySyncService;

	@Operation(summary = "出清规则列表")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/list")
	public Result<List<ClearanceRule>> list() {
		return Result.success(clearanceRuleService.list(ArynTenantContextHolder.getTenantId()));
	}

	@SysLog("新增出清折扣规则")
	@Operation(summary = "新增出清规则")
	@SaCheckPermission("promotion:shipactivity:save")
	@PostMapping
	public Result<ClearanceRule> save(@RequestBody ClearanceRule rule) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		ClearanceRule saved = clearanceRuleService.save(tenantId, rule);
		syncQuietly(tenantId);
		return Result.success(saved);
	}

	@SysLog("修改出清折扣规则")
	@Operation(summary = "修改出清规则")
	@SaCheckPermission("promotion:shipactivity:save")
	@PutMapping("/{id}")
	public Result<ClearanceRule> update(@PathVariable String id, @RequestBody ClearanceRule rule) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		rule.setId(id);
		ClearanceRule saved = clearanceRuleService.update(tenantId, rule);
		syncQuietly(tenantId);
		return Result.success(saved);
	}

	@SysLog("删除出清折扣规则")
	@Operation(summary = "删除出清规则")
	@SaCheckPermission("promotion:shipactivity:save")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		clearanceRuleService.delete(tenantId, id);
		syncQuietly(tenantId);
		return Result.success();
	}

	private void syncQuietly(String tenantId) {
		try {
			mallZoneActivitySyncService.syncTenant(tenantId);
		}
		catch (Exception ex) {
			log.warn("出清规则保存后同步失败，等待每日兜底任务 tenant={}", tenantId, ex);
		}
	}

}
