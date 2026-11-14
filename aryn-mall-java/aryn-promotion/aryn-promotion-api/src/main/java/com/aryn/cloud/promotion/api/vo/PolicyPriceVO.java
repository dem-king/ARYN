package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 协议价批量查询出参（单 SKU）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "协议价VO")
public class PolicyPriceVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "命中的价格政策ID")
	private String policyId;

	@Schema(description = "政策名称（展示「协议价」来源）")
	private String policyName;

	@Schema(description = "协议价（固定价或按售价折算后的到手价）")
	private BigDecimal policyPrice;

	@Schema(description = "当前售价（划线基准）")
	private BigDecimal originalPrice;

	@Schema(description = "协议起订量（展示提示，不参与计价校验）")
	private Integer minQty;

}
