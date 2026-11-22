package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * C 端阶梯价展示 VO（商详阶梯表 / 结算「再买 N 件单价降至 X」进度提示）。
 *
 * <p>展示口径：按 SKU 返回命中活动的档位表（minQty 升序）；同 SKU 多活动命中时
 * 取最高档单价最低者（确定性展示），成交仍以引擎试算为准（总价优惠最大者胜出）。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Data
@Schema(description = "C端阶梯价展示VO")
public class AppLadderPriceVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "展示归属活动ID")
	private String activityId;

	@Schema(description = "展示归属活动名称")
	private String activityName;

	@Schema(description = "档位表（minQty 升序）")
	private List<LadderTier> ladders = new ArrayList<>();

	/** 单个档位：买满 minQty 件时单价 unitPrice。 */
	@Data
	@Schema(description = "阶梯档位")
	public static class LadderTier implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "起订件数")
		private Integer minQty;

		@Schema(description = "档位单价（元）")
		private BigDecimal unitPrice;

	}

}