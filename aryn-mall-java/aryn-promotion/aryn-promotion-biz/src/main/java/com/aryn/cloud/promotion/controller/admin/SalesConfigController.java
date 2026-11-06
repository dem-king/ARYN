package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.SalesConfigSaveDTO;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.api.vo.SalesConfigVO;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-销售业绩全局配置
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/config")
@Tag(name = "销售业绩全局配置", description = "默认提成比例、转交奖励比例、台账生成日、退款观察期、认领有效期")
public class SalesConfigController {

	private final ISalesConfigService salesConfigService;

	@Operation(summary = "读取销售配置（未配置返回默认值）")
	@SaCheckPermission("promotion:salesconfig:page")
	@GetMapping
	public Result<SalesConfigVO> get() {
		SalesConfig config = salesConfigService.getActiveConfigOrDefaults();
		SalesConfigVO vo = new SalesConfigVO();
		BeanUtils.copyProperties(config, vo);
		return Result.success(vo);
	}

	@SysLog("编辑销售配置")
	@Operation(summary = "保存销售配置")
	@SaCheckPermission("promotion:salesconfig:edit")
	@PutMapping
	public Result<Boolean> save(@Valid @RequestBody SalesConfigSaveDTO dto) {
		return Result.success(salesConfigService.saveConfig(dto));
	}

}
