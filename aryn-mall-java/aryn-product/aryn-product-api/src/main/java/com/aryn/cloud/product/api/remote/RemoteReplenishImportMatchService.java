package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;

import java.util.List;

/**
 * 补给单 Excel 导入行匹配远程接口。
 *
 * <p>由 aryn-product-biz 实现，供订单域解析补给清单时批量匹配 SKU。
 * 匹配口径（与《C2-需求确认单》一致）：
 * <ol>
 *   <li>编码优先：IMPA &gt; ISSA &gt; 内部编码 &gt; 条码 &gt; 供应商编码；</li>
 *   <li>编码未命中时按「品名 + 规格」兜底；</li>
 *   <li>一个编码值命中多个 SKU 时返回 AMBIGUOUS 并给候选，不自动入单。</li>
 * </ol>
 *
 * <p>匹配**不校验数量规则**（MOQ/步长）与库存是否充足，只如实返回
 * {@code stock/moq/stepQty/skuStatus}，由订单域统一出报告，避免两套判定漂移。
 *
 * @author aryn
 * @since 2026/9/22
 */
public interface RemoteReplenishImportMatchService {

	/**
	 * 按 SKU ID 精确回查当前商品状态（**不过滤上下架**）。
	 *
	 * <p>补给单确认并入前必须再查一次：用户可能在报告页停留期间商品已下架，
	 * 或人工补选了一个不可售的 SKU。用「排除下架」的查询会查不到，
	 * 反而把「已下架」误判成「不存在」。
	 *
	 * @return 按 skuId 索引的结果，仅包含实际存在的 SKU
	 */
	List<ReplenishImportMatchVO> matchSkuIds(String tenantId, List<String> skuIds);

	/**
	 * 批量匹配导入行。
	 * @param tenantId 租户ID
	 * @param rows 导入行原文（编码/品名/规格/数量）
	 * @return 与入参同序、同长度的匹配结果；行内字段缺失时返回未命中
	 */
	List<ReplenishImportMatchVO> matchRows(String tenantId, List<ReplenishImportMatchDTO> rows);

}
