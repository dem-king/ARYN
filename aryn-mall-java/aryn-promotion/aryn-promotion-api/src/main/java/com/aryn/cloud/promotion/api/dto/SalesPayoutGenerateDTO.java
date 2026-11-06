package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度发放台账生成请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "月度发放台账生成请求")
public class SalesPayoutGenerateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "台账月份（yyyy-MM），为空取上月")
	private String payoutMonth;

}
