package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPU 社证统计条目 DTO（订单域 T+1 聚合结果，跨域 Dubbo 传输）。
 *
 * <p>只承载展示门槛/取整所需的三个计数，不携带日期——消费方（商品域卡面标签）
 * 只关心最新一期的量级，日期语义由统计表自身维护。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "SPU社证统计条目DTO")
public class GoodsSpuSalesStatDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "近N天有效订单去重买家数")
	private Integer buyerCnt;

	@Schema(description = "近N天购买≥2次的去重买家数")
	private Integer repeatBuyerCnt;

	@Schema(description = "回购率% = repeat/buyer*100")
	private BigDecimal repurchaseRate;

}
