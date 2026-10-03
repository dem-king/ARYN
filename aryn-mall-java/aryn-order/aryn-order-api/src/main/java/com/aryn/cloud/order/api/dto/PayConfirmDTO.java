package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 货到付款确认收款 DTO。
 *
 * <p>客户线下付款可能有折扣（应收 1803 实付 1800），确认收款时登记实收金额
 * 并可上传付款凭证（素材 ID 列表，服务端快照访问 URL）。
 *
 * @author aryn
 * @since 2026/10/3
 */
@Data
@Schema(description = "货到付款确认收款DTO")
public class PayConfirmDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "实收金额（元，必填，不能大于应收金额）")
	@NotNull(message = "实收金额不能为空")
	@DecimalMin(value = "0.01", message = "实收金额必须大于0")
	private BigDecimal actualPayPrice;

	@Schema(description = "付款凭证素材ID列表（图片，最多6张，可空）")
	@Size(max = 6, message = "付款凭证最多上传6张")
	private List<String> voucherMaterialIds;

}
