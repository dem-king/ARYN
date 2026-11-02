package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 展示信息：用于活动等只持久化 SKU ID 的配置回显商品名与售价。
 *
 * <p>只含展示字段，不含成本价等内部经营数据。
 *
 * @author aryn
 * @since 2026/10/31
 */
@Data
@Schema(description = "SKU展示信息")
public class GoodsSkuDisplayVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "规格描述，如「500ml」")
	private String skuName;

	@Schema(description = "售价（元）")
	private BigDecimal salesPrice;

	@Schema(description = "SKU 图片")
	private String picUrl;

}
