package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.SalesPayoutConfirmDTO;
import com.aryn.cloud.promotion.api.dto.SalesPayoutGenerateDTO;
import com.aryn.cloud.promotion.api.entity.SalesPayout;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;
import com.aryn.cloud.promotion.api.vo.SalesPayoutVO;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.util.SalesExcelExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 管理端-销售提成发放台账
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/payout")
@Tag(name = "销售提成发放台账", description = "月度台账生成、确认发放与导出")
public class SalesPayoutController {

	private final ISalesPayoutService salesPayoutService;

	@Operation(summary = "台账分页")
	@SaCheckPermission("promotion:salespayout:page")
	@GetMapping("/page")
	public Result<IPage<SalesPayoutVO>> page(Page<SalesPayout> page, SalesPayout query) {
		IPage<SalesPayout> result = salesPayoutService.selectAdminPage(page, query);
		return Result.success(result.convert(SalesPayoutController::toVO));
	}

	@Operation(summary = "台账详情")
	@SaCheckPermission("promotion:salespayout:page")
	@GetMapping("/{id}")
	public Result<SalesPayoutVO> get(@PathVariable String id) {
		return Result.success(toVO(salesPayoutService.getDetail(id)));
	}

	@Operation(summary = "批次内业绩单分页")
	@SaCheckPermission("promotion:salespayout:page")
	@GetMapping("/{id}/order/page")
	public Result<IPage<SalesOrderVO>> orderPage(Page<SalesPayout> page, @PathVariable String id) {
		return Result.success(salesPayoutService.pageOrders(page, id));
	}

	@SysLog("生成销售提成台账")
	@Operation(summary = "生成月度台账（幂等）")
	@SaCheckPermission("promotion:salespayout:generate")
	@PostMapping("/generate")
	public Result<Integer> generate(@RequestBody(required = false) SalesPayoutGenerateDTO dto) {
		String payoutMonth = dto == null ? null : dto.getPayoutMonth();
		return Result.success(salesPayoutService.generate(payoutMonth));
	}

	@SysLog("确认发放销售提成")
	@Operation(summary = "财务确认发放")
	@SaCheckPermission("promotion:salespayout:confirm")
	@PostMapping("/confirm")
	public Result<Boolean> confirm(@Valid @RequestBody SalesPayoutConfirmDTO dto) {
		return Result.success(salesPayoutService.confirm(dto));
	}

	@SysLog("销售提成台账导出")
	@Operation(summary = "台账导出")
	@SaCheckPermission("promotion:salespayout:export")
	@GetMapping("/export")
	public void export(HttpServletResponse response, SalesPayout query) throws IOException {
		List<SalesPayout> payouts = salesPayoutService.listForExport(query);
		SalesExcelExporter.prepareResponse(response, "销售提成发放台账");
		SalesExcelExporter.writePayouts(payouts, response.getOutputStream());
	}

	private static SalesPayoutVO toVO(SalesPayout payout) {
		if (payout == null) {
			return null;
		}
		SalesPayoutVO vo = new SalesPayoutVO();
		BeanUtils.copyProperties(payout, vo);
		return vo;
	}

}
