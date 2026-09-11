package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;

import java.util.List;

/**
 * 商品批量导入服务：模板下载 → 文件上传解析 → 预览校验 → 确认导入。
 *
 * @author aryn
 * @since 2026/9/11
 */
public interface ProductImportService {

	/**
	 * 预览校验：创建导入任务，逐行校验并落错误行，不改动商品数据。
	 */
	ProductImportPreviewVO preview(String tenantId, String fileName, List<ProductImportRowDTO> rows);

	/**
	 * 确认导入：重新校验后写入有效行，商品资料变更写入审计日志。
	 * @return 实际导入成功行数
	 */
	int confirmImport(String tenantId, String jobId, String fileName, List<ProductImportRowDTO> rows,
			String operatorId, String operatorName);

	/**
	 * 查询任务错误明细。
	 */
	List<ProductImportError> listErrors(String tenantId, String jobId);

}
