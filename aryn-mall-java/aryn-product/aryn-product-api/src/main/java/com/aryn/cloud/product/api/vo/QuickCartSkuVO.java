package com.aryn.cloud.product.api.vo;

import com.aryn.cloud.product.api.entity.GoodsSku;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 快捷加购可选 SKU。
 *
 * <p>只暴露购买决策所需的字段：成本价、租户、逻辑删除等内部字段不下发到 C 端。
 *
 * @author aryn
 * @since 2026/9/21
 */
@Data
@Schema(description = "快捷加购可选SKU")
public class QuickCartSkuVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "售价（元）")
	private BigDecimal salesPrice;

	@Schema(description = "可售库存")
	private Integer stock;

	@Schema(description = "SKU 图片")
	private String picUrl;

	@Schema(description = "规格描述，如「500ml；红色」")
	private String specsInfo;

	/**
	 * 规格明细。多规格商品必须靠它渲染规格选择区并做组合匹配，
	 * 复用 GoodsSku.Specs 保证与商品详情页的 SKU 结构一致。
	 */
	@Schema(description = "规格明细")
	private List<GoodsSku.Specs> specsArr;

	@Schema(description = "最小起订量")
	private Integer moq;

	@Schema(description = "数量步长")
	private Integer stepQty;

	@Schema(description = "采购单位")
	private String purchaseUnit;

}
