package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.vo.ReplenishCatalogVO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;

import java.util.List;

/**
 * 补给单 Excel 导入行匹配远程接口。
 *
 * <p>由 aryn-product-biz 实现，供订单域解析补给清单时批量匹配 SKU。
 * 匹配口径（与《C2-需求确认单》一致，船供资料下线后于 2026-09-29 调整）：
 * <ol>
 *   <li>编码优先：编码列即 SKU 编号（{@code goods_sku.id}），精确唯一命中；</li>
 *   <li>编码未命中时按「品名 + 规格」兜底；</li>
 *   <li>品名命中多个 SKU 时返回 AMBIGUOUS 并给候选，不自动入单。</li>
 * </ol>
 *
 * <p>匹配**不校验数量规则**（MOQ/步长）与库存是否充足，只如实返回
 * {@code stock/moq/stepQty/skuStatus}（船供包装资料下线后 moq/stepQty 恒空，
 * 由订单域按 1 兜底并统一出报告，避免两套判定漂移。
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

	/**
	 * 导出在售商品目录：按分类排序的全量可在售 SKU，供「下载标准模板」生成
	 * 客户可直接填数量的采购清单。
	 *
	 * <p>只回传**可导入**的商品（SKU 与 SPU 均在售），因为模板的用途是让客户
	 * 改个数量就回传：把下架商品列进去，客户填的量注定在报告里被标成「已下架」。
	 *
 * <p>编码列即 SKU 编号 —— 导出与回查共用同一稳定键，否则会出现
 * 「导出的编码自己解析不出来」。
	 *
	 * @param limit 最多导出的商品行数（单文件导入上限；超出时截断并回传总数）
	 */
	ReplenishCatalogVO exportCatalog(String tenantId, int limit);

}
