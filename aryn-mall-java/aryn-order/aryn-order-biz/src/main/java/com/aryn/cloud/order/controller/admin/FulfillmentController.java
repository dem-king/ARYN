package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.FulfillmentExceptionDTO;
import com.aryn.cloud.order.api.dto.FulfillmentPickScanDTO;
import com.aryn.cloud.order.api.dto.FulfillmentShortReportDTO;
import com.aryn.cloud.order.api.dto.FulfillmentWaveCreateDTO;
import com.aryn.cloud.order.api.entity.FulfillmentException;
import com.aryn.cloud.order.api.entity.FulfillmentPickItem;
import com.aryn.cloud.order.api.entity.FulfillmentWave;
import com.aryn.cloud.order.service.IFulfillmentService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 履约工作台管理端：波次、扫码拣货、复核交接和异常处理。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/fulfillment")
@Tag(description = "fulfillment-admin", name = "履约工作台")
public class FulfillmentController {

	private final IFulfillmentService fulfillmentService;

	@Operation(summary = "创建拣货波次")
	@SaCheckPermission("fulfillment:wave:save")
	@PostMapping("/wave")
	public Result<FulfillmentWave> createWave(@Valid @RequestBody FulfillmentWaveCreateDTO dto) {
		return Result.success(fulfillmentService.createWave(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), dto));
	}

	@Operation(summary = "波次分页（按计划交付时间排序）")
	@SaCheckPermission("fulfillment:wave:page")
	@GetMapping("/wave/page")
	public Result<IPage<FulfillmentWave>> wavePage(Page<FulfillmentWave> page, FulfillmentWave query) {
		return Result.success(fulfillmentService.wavePage(ArynTenantContextHolder.getTenantId(), page, query));
	}

	@Operation(summary = "波次拣货明细")
	@SaCheckPermission("fulfillment:wave:page")
	@GetMapping("/wave/{waveId}/items")
	public Result<List<FulfillmentPickItem>> waveItems(@PathVariable String waveId) {
		return Result.success(fulfillmentService.listWaveItems(ArynTenantContextHolder.getTenantId(), waveId));
	}

	@Operation(summary = "订单加入波次")
	@SaCheckPermission("fulfillment:wave:save")
	@PostMapping("/wave/{waveId}/orders")
	public Result<List<FulfillmentPickItem>> addOrder(@PathVariable String waveId, @RequestParam String orderId) {
		return Result.success(fulfillmentService.addOrderToWave(ArynTenantContextHolder.getTenantId(), waveId, orderId));
	}

	@Operation(summary = "扫码拣货")
	@SaCheckPermission("fulfillment:wave:pick")
	@PostMapping("/pick/scan")
	public Result<FulfillmentPickItem> scanPick(@Valid @RequestBody FulfillmentPickScanDTO dto) {
		return Result.success(fulfillmentService.scanPick(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), dto));
	}

	@Operation(summary = "报告短装")
	@SaCheckPermission("fulfillment:wave:pick")
	@PostMapping("/pick/short")
	public Result<FulfillmentPickItem> reportShort(@Valid @RequestBody FulfillmentShortReportDTO dto) {
		return Result.success(fulfillmentService.reportShort(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), dto));
	}

	@Operation(summary = "复核波次")
	@SaCheckPermission("fulfillment:wave:review")
	@PostMapping("/wave/{waveId}/review")
	public Result<FulfillmentWave> review(@PathVariable String waveId) {
		return Result.success(fulfillmentService.reviewWave(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), waveId));
	}

	@Operation(summary = "交接司机")
	@SaCheckPermission("fulfillment:wave:hand-over")
	@PostMapping("/wave/{waveId}/hand-over")
	public Result<FulfillmentWave> handOver(@PathVariable String waveId, @RequestParam(required = false) String staffId) {
		return Result.success(fulfillmentService.handOverToDriver(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), waveId, staffId));
	}

	@Operation(summary = "上报履约异常")
	@SaCheckPermission("fulfillment:exception:save")
	@PostMapping("/exception")
	public Result<FulfillmentException> reportException(@Valid @RequestBody FulfillmentExceptionDTO dto) {
		return Result.success(fulfillmentService.reportException(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), dto));
	}

	@Operation(summary = "关闭异常")
	@SaCheckPermission("fulfillment:exception:close")
	@PostMapping("/exception/{exceptionId}/close")
	public Result<FulfillmentException> closeException(@PathVariable String exceptionId,
			@RequestParam(required = false) String handleRemark) {
		return Result.success(fulfillmentService.closeException(ArynTenantContextHolder.getTenantId(),
				StpUtil.getLoginIdAsString(), exceptionId, handleRemark));
	}

	@Operation(summary = "异常分页")
	@SaCheckPermission("fulfillment:exception:page")
	@GetMapping("/exception/page")
	public Result<IPage<FulfillmentException>> exceptionPage(Page<FulfillmentException> page,
			FulfillmentException query) {
		return Result.success(fulfillmentService.exceptionPage(ArynTenantContextHolder.getTenantId(), page, query));
	}

	@Operation(summary = "港口配送看板（按港口与日期聚合波次状态）")
	@SaCheckPermission("fulfillment:wave:page")
	@GetMapping("/port-board")
	public Result<java.util.Map<String, Object>> portBoard(@RequestParam("portCode") String portCode,
			@RequestParam(value = "date", required = false) String date) {
		java.time.LocalDate boardDate = date != null && !date.isBlank()
				? java.time.LocalDate.parse(date)
				: java.time.LocalDate.now();
		return Result.success(fulfillmentService.portBoard(ArynTenantContextHolder.getTenantId(), portCode, boardDate));
	}

}
