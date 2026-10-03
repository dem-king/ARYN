package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品导入行 DTO（结构化行，由前端解析 CSV/Excel 后提交）。
 *
 * <p>新增商品不填 match 信息；更新商品必须通过 SKU 编号（{@code goods_sku.id}）匹配，
 * 禁止按名称覆盖。船供资料列（英文名/销售范围/IMPA 等）已随船供化下线（2026-09-29）。
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

	@Schema(description = "更新匹配值（SKU 编号，即 goods_sku.id；空表示新增）")
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

}
