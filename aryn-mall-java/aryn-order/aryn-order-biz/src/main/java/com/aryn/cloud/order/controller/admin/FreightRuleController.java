package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.entity.FreightRule;
import com.aryn.cloud.order.service.IFreightRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运费规则管理（满额包邮）
 *
 * @author aryn
 * @since 2026/11/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/freight-rule")
@Tag(description = "delivery-freight-rule", name = "运费规则管理")
public class FreightRuleController {

	private final IFreightRuleService freightRuleService;

	@Operation(summary = "运费规则分页列表")
	@SaCheckPermission("delivery:freight:page")
	@GetMapping("/page")
	public Result<IPage<FreightRule>> page(Page page, FreightRule freightRule) {
		return Result.success(freightRuleService.page(page, Wrappers.<FreightRule>lambdaQuery()
			.like(StrUtil.isNotBlank(freightRule.getRuleName()), FreightRule::getRuleName,
					freightRule.getRuleName())
			.eq(StrUtil.isNotBlank(freightRule.getBizScene()), FreightRule::getBizScene,
					freightRule.getBizScene())
			.eq(StrUtil.isNotBlank(freightRule.getDeliveryWay()), FreightRule::getDeliveryWay,
					freightRule.getDeliveryWay())
			.eq(StrUtil.isNotBlank(freightRule.getEnabled()), FreightRule::getEnabled,
					freightRule.getEnabled())
			.orderByDesc(FreightRule::getPriority)
			.orderByDesc(FreightRule::getCreateTime)));
	}

	@Operation(summary = "启用中的运费规则")
	@SaCheckPermission("delivery:freight:page")
	@GetMapping("/list")
	public Result<List<FreightRule>> list() {
		return Result.success(freightRuleService.listEnabled());
	}

	@Operation(summary = "新增运费规则")
	@SysLog("新增运费规则")
	@SaCheckPermission("delivery:freight:add")
	@PostMapping
	public Result<Boolean> save(@RequestBody FreightRule freightRule) {
		return Result.success(freightRuleService.saveRule(freightRule));
	}

	@Operation(summary = "修改运费规则")
	@SysLog("修改运费规则")
	@SaCheckPermission("delivery:freight:edit")
	@PutMapping
	public Result<Boolean> update(@RequestBody FreightRule freightRule) {
		return Result.success(freightRuleService.updateRule(freightRule));
	}

	@Operation(summary = "逻辑删除运费规则")
	@SysLog("删除运费规则")
	@SaCheckPermission("delivery:freight:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> remove(@PathVariable String id) {
		return Result.success(freightRuleService.removeById(id));
	}

}
