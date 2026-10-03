package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 统一商品目录行摘要（C 端场景选货页 / 搜索共用）。
 *
 * <p>船供资料（IMPA/ISSA/采购单位/MOQ/步长）已随船供化下线（2026-09-29），
 * 目录只保留下单必需字段；数量规则由 C 端按默认 1 兜底。
 *
 * @author aryn
 * @since 2026/9/29
 */
@Data
@Schema(description = "统一商品目录行摘要")
public class GoodsCatalogSummaryVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "列表缩略图（SKU 图优先，回退 SPU 首图）")
	private String picUrl;

	@Schema(description = "库存")
	private Integer stock;

	@Schema(description = "售价")
	private BigDecimal salesPrice;

	@Schema(description = "商品状态：1上架 0下架")
	private String status;

	@Schema(description = "搜索关键词")
	private String keyword;

}
