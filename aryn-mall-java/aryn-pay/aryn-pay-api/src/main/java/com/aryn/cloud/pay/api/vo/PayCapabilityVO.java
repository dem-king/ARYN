
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 支付能力下发模型。只下发已实现、已启用且本地材料齐备的渠道。
 */
@Data
@Schema(description = "支付能力")
public class PayCapabilityVO {

	private String tradeType;

	private String channel;

	private String terminalType;

	private String invokeType;

	private boolean available;

	@Schema(description = "不可用原因；可用时为空")
	private String reason;

}
