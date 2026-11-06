package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 佣金兑换优惠券请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "佣金兑换优惠券请求")
public class DistributionCouponExchangeDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "券包ID")
	@NotBlank(message = "请选择券包")
	private String packId;

}
