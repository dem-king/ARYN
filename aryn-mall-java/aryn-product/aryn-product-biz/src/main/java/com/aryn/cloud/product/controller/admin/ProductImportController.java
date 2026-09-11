package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.excel.EasyExcel;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;
import com.aryn.cloud.product.service.ProductImportService;
import com.aryn.cloud.product.service.impl.ProductExcelConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 商品批量导入管理端：Excel 模板下载 → 文件上传解析 → 预览校验 → 确认导入。
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

	@Operation(summary = "下载 Excel 导入模板（首行为标题行）")
	@SaCheckPermission("product:import:template")
	@GetMapping("/template")
	public void template(HttpServletResponse response) throws IOException {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding("utf-8");
		String fileName = URLEncoder.encode("商品导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
		EasyExcel.write(response.getOutputStream()).sheet("商品导入").head(ProductExcelConverter.templateHead())
			.doWrite(java.util.Collections.emptyList());
	}

	@Operation(summary = "上传 Excel 并预览校验（解析行落库，确认时服务端重新校验）")
	@SaCheckPermission("product:import:preview")
	@PostMapping("/upload")
	public Result<ProductImportPreviewVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
		return Result.success(productImportService.previewExcel(SecurityUtils.getTenantId(), file.getOriginalFilename(),
				file.getInputStream()));
	}

	@Operation(summary = "确认导入（按任务ID，服务端从解析行重新校验后写入）")
	@SaCheckPermission("product:import:confirm")
	@PostMapping("/{jobId}/confirm")
	public Result<Integer> confirm(@PathVariable String jobId) {
		String operatorId = StpUtil.getLoginIdAsString();
		int imported = productImportService.confirmImport(SecurityUtils.getTenantId(), jobId, operatorId, operatorId);
		return Result.success(imported);
	}

	@Operation(summary = "任务错误明细")
	@SaCheckPermission("product:import:preview")
	@GetMapping("/{jobId}/errors")
	public Result<List<ProductImportError>> errors(@PathVariable String jobId) {
		return Result.success(productImportService.listErrors(SecurityUtils.getTenantId(), jobId));
	}

}
