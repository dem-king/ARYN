package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.ReplenishDirectImportConfirmDTO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportConfirmVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportVO;
import com.aryn.cloud.order.service.IReplenishDirectImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 清单直下单 C 端接口：不挂共享购物车的 Excel 导入链路。
 *
 * <p>「导入清单下单」向导的解析与确认；确认产出**已复核**的 SKU 清单，
 * 由前端带进结算页走既有下单（createWay=2）。模板下载复用
 * {@code GET /app/shared-cart/import/template}（本就不依赖购物车）。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Tag(description = "replenish-direct-import", name = "清单直下单导入")
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/replenish-import")
public class AppReplenishImportController {

	private final IReplenishDirectImportService replenishDirectImportService;

	@Operation(summary = "上传清单 Excel，解析并返回导入报告")
	@PostMapping("/preview")
	public Result<ReplenishDirectImportVO> preview(
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws IOException {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.preview(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), file.getOriginalFilename(), file.getSize(), file.getBytes()));
	}

	@Operation(summary = "取回导入报告（稍后处理）")
	@GetMapping("/imports/{importId}")
	public Result<ReplenishDirectImportVO> getImport(@PathVariable String importId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.getImport(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), importId));
	}

	@Operation(summary = "最近一次待确认的导入报告（无则返回 null）")
	@GetMapping("/pending")
	public Result<ReplenishDirectImportVO> pending() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.findPending(ArynTenantContextHolder.getTenantId(),
				user.getUserId()));
	}

	@Operation(summary = "最近一次可继续的导入（待确认或已确认；重启恢复用，无则返回 null）")
	@GetMapping("/latest")
	public Result<ReplenishDirectImportVO> latest() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.findLatest(ArynTenantContextHolder.getTenantId(),
				user.getUserId()));
	}

	@Operation(summary = "恢复已确认导入的复核清单（只读；杀进程后重新进入结算）")
	@GetMapping("/imports/{importId}/items")
	public Result<ReplenishDirectImportConfirmVO> resume(@PathVariable String importId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.resume(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), importId));
	}

	@Operation(summary = "确认导入，返回已复核的 SKU 清单与跳过行（幂等；行内容以服务端解析行为准）")
	@PostMapping("/imports/{importId}/confirm")
	public Result<ReplenishDirectImportConfirmVO> confirm(@PathVariable String importId,
			@RequestBody(required = false) @Valid ReplenishDirectImportConfirmDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(replenishDirectImportService.confirm(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), importId, dto));
	}

}
