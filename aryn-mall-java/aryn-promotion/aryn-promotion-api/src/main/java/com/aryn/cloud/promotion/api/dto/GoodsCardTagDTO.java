package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品卡营销标签（SPU 级聚合）。
 *
 * <p>新人价/秒杀价都是 SKU 级配置，但商品卡在装修楼层/推荐流里只有 SPU 维度，
 * 因此远程出口按 SPU 聚合：同一 SPU 命中多个 SKU 时取最低可享价（与出清价的
 * SPU 聚合口径一致）。两个字段 null 都表示未命中，由展示端回落原售价。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "商品卡营销标签（SPU级聚合）")
public class GoodsCardTagDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "新人专享价；null=未命中（或当前用户无资格）")
	private BigDecimal newbiePrice;

	@Schema(description = "进行中秒杀价；null=未命中")
	private BigDecimal seckillPrice;

}
