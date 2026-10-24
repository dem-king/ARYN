package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "会员当前等级权益")
public class MemberBenefitsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String levelId;

	private String levelName;

	@Schema(description = "最优商品折扣率，范围(0,1]")
	private BigDecimal discountRate = BigDecimal.ONE;

	private boolean freeShipping;

	@Schema(description = "最优积分倍率，不小于1")
	private BigDecimal pointsMultiplier = BigDecimal.ONE;

	/**
	 * 等级专属积分抵扣上限（百分数，如 30.00 表示 30%）；null=该等级未配置，
	 * 抵扣上限回落全局配置（多档配置取最大：等级越高抵得越多，京东分档口径）。
	 */
	private BigDecimal deductMaxRatio;

	private List<String> exclusiveCouponTemplateIds = new ArrayList<>();

}
