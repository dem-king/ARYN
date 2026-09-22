package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;

import java.util.List;

/**
 * 补给单 Excel 导入行匹配服务（商品域）。
 *
 * @author aryn
 * @since 2026/9/22
 */
public interface IReplenishImportMatchService {

	/**
	 * 批量匹配导入行，返回与入参同序结果。
	 */
	List<ReplenishImportMatchVO> matchRows(String tenantId, List<ReplenishImportMatchDTO> rows);

	/**
	 * 按 SKU ID 精确回查（不过滤上下架）。
	 */
	List<ReplenishImportMatchVO> matchSkuIds(String tenantId, List<String> skuIds);

}
