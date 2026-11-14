package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.dto.PricePolicySaveDTO;
import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.aryn.cloud.promotion.service.impl.PricePolicyService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 价格政策（协议价）管理：B2B 分层价格的合同价层。
 *
 * <p>按采购主体/船舶/客群标签圈定适用对象，SKU 级固定价或折扣率；
 * 生效中的政策锁定成交基价，阶梯价/身份价/档期特供不可覆盖。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/price-policy")
@Tag(description = "price-policy", name = "价格政策（协议价）")
public class PricePolicyController {

	private final PricePolicyService pricePolicyService;

	@Operation(summary = "价格政策列表")
	@SaCheckPermission("promotion:pricepolicy:page")
	@GetMapping("/list")
	public Result<List<PricePolicy>> list(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {
		return Result.success(pricePolicyService.list(ArynTenantContextHolder.getTenantId(), status, keyword));
	}

	@Operation(summary = "价格政策详情（含 SKU 明细）")
	@SaCheckPermission("promotion:pricepolicy:page")
	@GetMapping("/{id}")
	public Result<Map<String, Object>> get(@PathVariable String id) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		PricePolicy policy = pricePolicyService.get(tenantId, id);
		if (policy == null) {
			return Result.success(null);
		}
		List<PricePolicyItem> items = pricePolicyService.listItems(tenantId, id);
		Map<String, Object> data = new HashMap<>(4);
		data.put("policy", policy);
		data.put("items", items);
		return Result.success(data);
	}

	@SysLog("新增价格政策")
	@Operation(summary = "新建价格政策（含 SKU 明细）")
	@SaCheckPermission("promotion:pricepolicy:save")
	@PostMapping
	public Result<PricePolicy> save(@RequestBody PricePolicySaveDTO dto) {
		return Result.success(pricePolicyService.save(ArynTenantContextHolder.getTenantId(),
				dto.getPolicy(), dto.getItems()));
	}

	@SysLog("修改价格政策")
	@Operation(summary = "修改价格政策（生效中不可改）")
	@SaCheckPermission("promotion:pricepolicy:save")
	@PutMapping("/{id}")
	public Result<PricePolicy> update(@PathVariable String id, @RequestBody PricePolicySaveDTO dto) {
		return Result.success(pricePolicyService.update(ArynTenantContextHolder.getTenantId(),
				id, dto.getPolicy(), dto.getItems()));
	}

	@Operation(summary = "价格政策 dry-run（发布前冲突/倒挂预检）")
	@SaCheckPermission("promotion:pricepolicy:page")
	@GetMapping("/{id}/dry-run")
	public Result<com.aryn.cloud.promotion.api.vo.PolicyDryRunVO> dryRun(@PathVariable String id) {
		return Result.success(pricePolicyService.dryRun(ArynTenantContextHolder.getTenantId(), id));
	}

	@SysLog("发布/停用价格政策")
	@Operation(summary = "切换价格政策状态（1生效 2停用 0草稿）")
	@SaCheckPermission("promotion:pricepolicy:publish")
	@PutMapping("/{id}/status/{targetStatus}")
	public Result<PricePolicy> changeStatus(@PathVariable String id, @PathVariable String targetStatus) {
		return Result.success(pricePolicyService.changeStatus(
				ArynTenantContextHolder.getTenantId(), id, targetStatus));
	}

	@SysLog("删除价格政策")
	@Operation(summary = "删除价格政策（含明细，逻辑删除）")
	@SaCheckPermission("promotion:pricepolicy:del")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		pricePolicyService.delete(ArynTenantContextHolder.getTenantId(), id);
		return Result.success();
	}

}
