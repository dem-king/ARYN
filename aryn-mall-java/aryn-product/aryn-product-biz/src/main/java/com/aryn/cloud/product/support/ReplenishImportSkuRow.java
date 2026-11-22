package com.aryn.cloud.product.support;

import com.aryn.cloud.product.api.entity.GoodsSku;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 补给单导入匹配用的 SKU 行（SKU + SPU 一次联表取出）。
 *
 * <p>刻意不加下架过滤：导入报告要能区分「未匹配」与「已下架」，
 * 若把下架数据过滤掉，用户永远看不到真实原因（见
 * {@code RemoteReplenishImportMatchService} 的注释）。
 *
 * <p>船供包装资料（采购单位/MOQ/步长）与船供编码已下线（2026-09-29），
 * 本轮以SKU商品事实重新引入度量版本及门槛，未审核存量不推断重量。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
public class ReplenishImportSkuRow {

	private String skuId;
	private String identifierType;
	private String identifierValue;

	private String spuId;

	private String name;

	/** SPU 状态：0下架 1上架 */
	private String spuStatus;

	/** SKU 状态：0正常 1下架 */
	private String skuStatus;

	private BigDecimal salesPrice;

	private Integer stock;

	private String salesUnit;

	private String baseDimension;

	private String baseUnit;

	private BigDecimal baseQtyPerSalesUnit;

	private String quantityPrecision;

	private Integer minOrderQty;

	private Integer stepQty;

	private String unitSpecText;

	private Long measurementRevision;

	private List<GoodsSku.Specs> specsArr;

	// ------------------------------------------------------------------
	// 以下字段仅供「模板目录导出」使用（导入行匹配不读）
	// ------------------------------------------------------------------

	/** 一级类目名（模板按分类分节时用） */
	private String categoryFirstName;

	/** 二级类目名（模板按分类分节时用） */
	private String categorySecondName;

	/** 一级类目排序（让导出顺序稳定，不随查询计划漂移） */
	private Integer categoryFirstSort;

	/** 二级类目排序 */
	private Integer categorySecondSort;

}
