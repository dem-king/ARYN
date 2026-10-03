package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 补给单导入模板的一行商品（商品域 → 订单域，用于生成「按分类的采购目录」）。
 *
 * <p>与 {@link ReplenishImportMatchVO} 的区别：那个是「用户上传的行匹配到了谁」，
 * 这个是「在售商品有哪些」，方向相反 —— 模板下载先取全量目录，
 * 客户在原表上填数量后回传，才走匹配链路。
 *
 * <p>刻意只回传**可下单**的字段（编码/品名/规格/库存/售价）：
 * 成本价等内部字段不得出现在下发给客户的 Excel 里（与 C 端成本价脱敏同一口径）。
 * 船供包装资料（采购单位/MOQ/步长）已下线（2026-09-29），
 * 模板不再提供这三列参考信息。
 *
 * @author aryn
 * @since 2026/9/28
 */
@Data
@Schema(description = "补给单导入模板行（在售商品目录）")
public class ReplenishCatalogRowVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	/** 一级/二级类目拼接（如「蔬果/蔬菜」）；缺一级时只给二级，都没有则为空 */
	@Schema(description = "分类展示名")
	private String categoryName;

	/**
	 * 商品编码值，即 SKU 编号（{@code goods_sku.id}）。
	 *
	 * <p>历史上取自船供编码（IMPA/ISSA/内部编码/条码），船供资料下线后
	 * 改为 SKU 编号 —— 与匹配链路 {@code matchRows} 的回查键一致，
	 * 保证「导出的编码自己必然解析得出来」。
	 */
	@Schema(description = "商品编码（SKU 编号）")
	private String code;

	@Schema(description = "商品名")
	private String name;

	@Schema(description = "规格描述（SKU 规格值拼接）")
	private String spec;

	@Schema(description = "库存")
	private Integer stock;

	@Schema(description = "售价")
	private BigDecimal salesPrice;

}
