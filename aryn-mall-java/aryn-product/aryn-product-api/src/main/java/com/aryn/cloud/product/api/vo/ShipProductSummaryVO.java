package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 船供商品摘要 VO（列表用，不携带富文本与全部 SKU）。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "船供商品摘要VO")
public class ShipProductSummaryVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "商品中文名")
	private String name;

	@Schema(description = "商品英文名")
	private String nameEn;

	@Schema(description = "销售范围：1仅个人购买 2仅船供采购 3个人和船供均可")
	private String saleScope;

	@Schema(description = "IMPA 编码")
	private String impaCode;

	@Schema(description = "统一关键词：同时模糊匹配 IMPA/ISSA/内部编码/条码/中英文品名/搜索别名")
	private String keyword;

	@Schema(description = "ISSA 编码")
	private String issaCode;

	@Schema(description = "内部物料编码")
	private String internalItemCode;

	@Schema(description = "条形码")
	private String barcode;

	@Schema(description = "基本单位")
	private String baseUnit;

	@Schema(description = "采购单位")
	private String purchaseUnit;

	@Schema(description = "箱规")
	private String packageSpec;

	@Schema(description = "最小起订量")
	private Integer moq;

	@Schema(description = "数量步长")
	private Integer stepQty;

	@Schema(description = "库存")
	private Integer stock;

	@Schema(description = "售价")
	private BigDecimal salesPrice;

	@Schema(description = "储存条件")
	private String storageType;

	@Schema(description = "商品上下架状态")
	private String status;

	@Schema(description = "资料完整度（0-100）")
	private Integer publishCompleteness;

}
