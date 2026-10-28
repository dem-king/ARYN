package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品热销榜条目 DTO（订单域聚合结果，跨域 Dubbo 传输）。
 *
 * <p>只承载榜单排序所需的最小快照字段：spuId 用于商品域回查当前在售商品，
 * spuName/picUrl 为下单时的商品快照，商品域会用当前商品信息覆盖。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Data
@Schema(description = "商品热销榜条目DTO")
public class GoodsSalesRankDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品名称快照")
	private String spuName;

	@Schema(description = "图片快照")
	private String picUrl;

	@Schema(description = "累计成交件数（近 N 天有效订单求和）")
	private Integer salesCount;

}
