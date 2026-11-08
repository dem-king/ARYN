package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 级进行中秒杀价（价格快照 Job 用）。
 *
 * <p>与 SPU 级的 {@link GoodsCardTagDTO} 不同：价格快照按 SKU 逐行落库，
 * 需要保留 SKU 维度（同 SPU 多 SKU 场次价不同时不能聚合取低后再落快照，
 * 否则快照价会与该 SKU 实际可享价漂移）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "SKU级进行中秒杀价")
public class GoodsSkuSeckillDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "进行中秒杀价；null=未命中")
	private BigDecimal seckillPrice;

}
