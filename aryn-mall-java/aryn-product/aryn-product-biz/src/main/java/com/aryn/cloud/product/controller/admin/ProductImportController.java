package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;
import com.aryn.cloud.product.service.ProductImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品批量导入管理端：模板下载 → 文件上传解析 → 预览校验 → 确认导入。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/product-import")
@Tag(description = "product-import", name = "商品批量导入")
public class ProductImportController {

	private final ProductImportService productImportService;

	@Operation(summary = "导入模板字段定义（前端据此生成 CSV 模板与解析列）")
	@SaCheckPermission("product:import:template")
	@GetMapping("/template")
	public Result<List<Map<String, String>>> template() {
		List<Map<String, String>> columns = List.of(
				Map.of("field", "name", "title", "商品中文名", "required", "true"),
				Map.of("field", "nameEn", "title", "英文名", "required", "false"),
				Map.of("field", "saleScope", "title", "销售范围(1/2/3)", "required", "false"),
				Map.of("field", "impaCode", "title", "IMPA编码", "required", "false"),
				Map.of("field", "issaCode", "title", "ISSA编码", "required", "false"),
				Map.of("field", "internalItemCode", "title", "内部物料编码", "required", "false"),
				Map.of("field", "barcode", "title", "条形码", "required", "false"),
				Map.of("field", "matchType", "title", "更新匹配方式(SKU/IMPA/INTERNAL)", "required", "false"),
				Map.of("field", "matchValue", "title", "更新匹配值", "required", "false"),
				Map.of("field", "categorySecondId", "title", "二级类目ID", "required", "false"),
				Map.of("field", "brandId", "title", "品牌ID", "required", "false"),
				Map.of("field", "salesPrice", "title", "售价(元)", "required", "false"),
				Map.of("field", "stock", "title", "库存", "required", "false"),
				Map.of("field", "purchaseUnit", "title", "采购单位", "required", "false"),
				Map.of("field", "packageSpec", "title", "箱规", "required", "false"),
				Map.of("field", "moq", "title", "最小起订量", "required", "false"),
				Map.of("field", "stepQty", "title", "数量步长", "required", "false"),
				Map.of("field", "storageType", "title", "储存条件(1-5)", "required", "false"));
		return Result.success(columns);
	}

	@Operation(summary = "预览校验（解析后的结构化行）")
	@SaCheckPermission("product:import:preview")
	@PostMapping("/preview")
	public Result<ProductImportPreviewVO> preview(@RequestBody ProductImportPayload payload) {
		return Result.success(productImportService.preview(SecurityUtils.getTenantId(), payload.getFileName(),
				payload.getRows()));
	}

	@Operation(summary = "确认导入")
	@SaCheckPermission("product:import:confirm")
	@PostMapping("/confirm")
	public Result<Integer> confirm(@RequestBody ProductImportPayload payload) {
		String operatorId = StpUtil.getLoginIdAsString();
		int imported = productImportService.confirmImport(SecurityUtils.getTenantId(), payload.getJobId(),
				payload.getFileName(), payload.getRows(), operatorId, operatorId);
		return Result.success(imported);
	}

	@Operation(summary = "任务错误明细")
	@SaCheckPermission("product:import:preview")
	@GetMapping("/{jobId}/errors")
	public Result<List<ProductImportError>> errors(@PathVariable String jobId) {
		return Result.success(productImportService.listErrors(SecurityUtils.getTenantId(), jobId));
	}

	@Data
	public static class ProductImportPayload {

		private String jobId;

		private String fileName;

		private List<ProductImportRowDTO> rows;

	}

}
