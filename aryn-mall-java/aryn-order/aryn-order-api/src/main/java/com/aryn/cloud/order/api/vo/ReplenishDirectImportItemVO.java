package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 清单直下单确认产出的明细项（同 SKU 已合并数量）。
 *
 * <p>确认接口的终点不是补给单明细，而是这份**已复核**的 SKU 清单：
 * 前端写进结算页商品清单后走既有下单链路（createWay=2），
 * 价格/库存由结算与下单服务端重算，这里的快照仅用于展示。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@Schema(description = "清单直下单确认明细项")
public class ReplenishDirectImportItemVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "采购数量")
	private Integer quantity;

	@Schema(description = "商品名")
	private String spuName;

	@Schema(description = "规格描述")
	private String specsInfo;

	@Schema(description = "商品图片（SKU 图，取不到为 null）")
	private String picUrl;

	@Schema(description = "当前售价（展示参考，实价以结算为准）")
	private BigDecimal salesPrice;

}
