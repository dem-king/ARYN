package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 短装报告 DTO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "短装报告DTO")
public class FulfillmentShortReportDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "波次ID")
	@NotBlank(message = "波次ID不能为空")
	private String waveId;

	@Schema(description = "拣货明细ID")
	@NotBlank(message = "拣货明细ID不能为空")
	private String itemId;

	@Schema(description = "实际可拣数量")
	@NotNull(message = "实际数量不能为空")
	private Integer actualQuantity;

	@Schema(description = "短装原因编码")
	@NotBlank(message = "短装原因不能为空")
	private String reasonCode;

	@Schema(description = "短装原因说明")
	private String reasonDesc;

	@Schema(description = "替代商品SKU ID（有替代品时填写）")
	private String substitutedSkuId;

}
