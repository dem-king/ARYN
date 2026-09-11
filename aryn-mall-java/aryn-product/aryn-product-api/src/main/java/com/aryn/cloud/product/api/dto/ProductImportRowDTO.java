package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品导入行 DTO（结构化行，由前端解析 CSV/Excel 后提交）。
 *
 * <p>新增商品不填 match 信息；更新商品必须通过 skuId、IMPA 或内部编码匹配，
 * 禁止按名称覆盖。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "商品导入行DTO")
public class ProductImportRowDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "行号（从1开始）")
	private Integer rowNo;

	@Schema(description = "商品中文名")
	private String name;

	@Schema(description = "商品英文名")
	private String nameEn;

	@Schema(description = "销售范围：1仅个人购买 2仅船供采购 3个人和船供均可（空视为1）")
	private String saleScope;

	@Schema(description = "IMPA 编码")
	private String impaCode;

	@Schema(description = "ISSA 编码")
	private String issaCode;

	@Schema(description = "内部物料编码")
	private String internalItemCode;

	@Schema(description = "条形码")
	private String barcode;

	@Schema(description = "更新匹配方式：SKU/IMPA/INTERNAL（空表示新增）")
	private String matchType;

	@Schema(description = "更新匹配值（SKU ID / IMPA 编码 / 内部编码）")
	private String matchValue;

	@Schema(description = "SKU ID（新增时可自带，更新按匹配结果）")
	private String skuId;

	@Schema(description = "二级类目ID")
	private String categorySecondId;

	@Schema(description = "品牌ID")
	private String brandId;

	@Schema(description = "售价（元）")
	private BigDecimal salesPrice;

	@Schema(description = "库存")
	private Integer stock;

	@Schema(description = "采购单位")
	private String purchaseUnit;

	@Schema(description = "箱规")
	private String packageSpec;

	@Schema(description = "最小起订量")
	private Integer moq;

	@Schema(description = "数量步长")
	private Integer stepQty;

	@Schema(description = "储存条件：1常温 2冷藏 3冷冻 4危险品 5其他")
	private String storageType;

}
