package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "会员当前等级权益")
public class MemberBenefitsVO {

	private String levelId;

	private String levelName;

	@Schema(description = "最优商品折扣率，范围(0,1]")
	private BigDecimal discountRate = BigDecimal.ONE;

	private boolean freeShipping;

	@Schema(description = "最优积分倍率，不小于1")
	private BigDecimal pointsMultiplier = BigDecimal.ONE;

	private List<String> exclusiveCouponTemplateIds = new ArrayList<>();

}
