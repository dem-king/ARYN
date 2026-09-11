package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 扫码拣货 DTO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "扫码拣货DTO")
public class FulfillmentPickScanDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "波次ID")
	@NotBlank(message = "波次ID不能为空")
	private String waveId;

	@Schema(description = "拣货明细ID")
	@NotBlank(message = "拣货明细ID不能为空")
	private String itemId;

	@Schema(description = "扫码得到的 SKU 条码（或 SKU ID）")
	@NotBlank(message = "条码不能为空")
	private String scannedCode;

	@Schema(description = "本次扫码确认数量")
	@NotNull(message = "数量不能为空")
	private Integer quantity;

}
