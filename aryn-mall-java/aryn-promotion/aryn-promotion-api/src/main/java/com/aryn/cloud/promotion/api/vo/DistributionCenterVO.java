package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分销中心汇总
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销中心汇总")
public class DistributionCenterVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "累计佣金")
	private BigDecimal totalCommission;

	@Schema(description = "可提现佣金")
	private BigDecimal availableCommission;

	@Schema(description = "已提现佣金")
	private BigDecimal withdrawnCommission;

	@Schema(description = "冻结佣金（提现申请中）")
	private BigDecimal frozenCommission;

	@Schema(description = "待审核提现笔数")
	private Long pendingWithdrawCount;

}
