package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户 RFM 分层统计。
 */
@Data
@Schema(description = "用户 RFM 分层统计")
public class UserRfmSegmentVO {

	@Schema(description = "分层名称")
	private String segment;

	@Schema(description = "用户数")
	private Long userCount;

	@Schema(description = "累计成交金额")
	private BigDecimal gmv;

}
