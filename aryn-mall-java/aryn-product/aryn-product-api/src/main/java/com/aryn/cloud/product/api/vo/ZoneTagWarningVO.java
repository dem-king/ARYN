package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 专区操作告警/提示。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "专区操作告警")
public class ZoneTagWarningVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "告警码：NO_RULE / ZONE_DEFAULT_RATE / INVERTED_PRICE / OFF_SHELF / EXPIRED / RECENT_SALES_INCREASE / SKU_OVERRIDE_PRESERVED / SKU_OVERRIDE_CLEARED / MEMBER_FIXED_KEPT")
	private String code;

	@Schema(description = "告警文案")
	private String message;

}
