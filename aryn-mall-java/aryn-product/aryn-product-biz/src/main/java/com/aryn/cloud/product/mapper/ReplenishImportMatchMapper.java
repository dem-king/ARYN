package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.support.ReplenishImportSkuRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 补给单导入匹配用查询：按 SKU ID / 品名批量取商品行。
 *
 * <p>与 {@code GoodsSkuMapper.selectSkuByIds} 的差别：后者会过滤下架与删除，
 * 只服务下单链路；导入报告需要看到下架原因，因此这里不过滤 status，
 * 由调用方决定报告口径。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Mapper
public interface ReplenishImportMatchMapper {

	/**
	 * 按 SKU ID 批量取商品行（不过滤上下架）。
	 */
	List<ReplenishImportSkuRow> selectRowsBySkuIds(@Param("tenantId") String tenantId,
			@Param("skuIds") List<String> skuIds);

	/**
	 * 按商品名精确匹配批量取商品行（用于「品名+规格」兜底匹配）。
	 */
	List<ReplenishImportSkuRow> selectRowsByExactNames(@Param("tenantId") String tenantId,
			@Param("names") List<String> names);

	/**
	 * 按商品名包含匹配批量取候选（关键词已去重、数量受限，避免全表扫描放大）。
	 */
	List<ReplenishImportSkuRow> selectRowsByKeywords(@Param("tenantId") String tenantId,
			@Param("keywords") List<String> keywords);

	/**
	 * 在售商品目录：按分类排序的全量可导入 SKU（SKU 与 SPU 均在售）。
	 *
	 * <p>与上面几个查询的差别：那些是「按用户给的条件找商品」，
	 * 这个是「把可采购的商品全列出来」给模板导出用，因此**必须过滤下架**——
	 * 下架商品填进模板只会在报告里被标成「已下架」。
	 *
	 * @param limit 行数上限（超过即截断，由调用方回传真实总数）
	 */
	List<ReplenishImportSkuRow> selectCatalogRows(@Param("tenantId") String tenantId, @Param("limit") int limit);

	/**
	 * 在售商品目录的真实总行数（未截断前），用于如实告知客户「模板只含前 N 项」。
	 */
	int countCatalogRows(@Param("tenantId") String tenantId);

}
