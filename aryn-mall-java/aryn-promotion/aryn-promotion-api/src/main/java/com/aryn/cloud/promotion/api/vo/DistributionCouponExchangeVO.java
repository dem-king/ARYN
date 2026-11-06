package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 券兑换记录视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "券兑换记录视图")
@EqualsAndHashCode(callSuper = true)
public class DistributionCouponExchangeVO extends DistributionCouponExchange {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "分销员当前昵称")
	private String userNickname;

}
