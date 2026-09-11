package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 常购商品条目 VO（按用户近 90 天有效订单统计）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "常购商品条目VO")
public class FrequentPurchaseVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "商品名称快照")
	private String spuName;

	@Schema(description = "规格快照")
	private String specsInfo;

	@Schema(description = "图片快照")
	private String picUrl;

	@Schema(description = "累计购买件数")
	private Integer totalQuantity;

	@Schema(description = "累计购买次数")
	private Integer orderCount;

	@Schema(description = "最近购买时间")
	private String lastPurchaseTime;

}
