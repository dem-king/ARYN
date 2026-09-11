package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;

import java.io.InputStream;
import java.util.List;

/**
 * 商品批量导入服务：模板下载 → Excel 上传解析 → 预览校验 → 确认导入。
 *
 * <p>解析行由服务端持久化，确认导入时服务端重新校验后写入，
 * 不依赖客户端回传行数据。
 *
 * @author aryn
 * @since 2026/9/11
 */
public interface ProductImportService {

	/**
	 * Excel 预览：解析上传文件，创建导入任务并落解析行，逐行校验并落错误行。
	 */
	ProductImportPreviewVO previewExcel(String tenantId, String fileName, InputStream excelStream);

	/**
	 * 结构化行预览（Excel 解析后的统一入口）。
	 */
	ProductImportPreviewVO preview(String tenantId, String fileName, List<ProductImportRowDTO> rows);

	/**
	 * 确认导入：从解析行读取数据，重新校验后写入有效行，商品资料变更写入审计日志。
	 * @return 实际导入成功行数
	 */
	int confirmImport(String tenantId, String jobId, String operatorId, String operatorName);

	/**
	 * 查询任务错误明细。
	 */
	List<ProductImportError> listErrors(String tenantId, String jobId);

}
