package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员权益可用的优惠券模板信息。
 */
@Data
@Schema(description = "优惠券模板信息")
public class CouponTemplateInfoVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "模板ID")
	private String id;

	@Schema(description = "优惠券名称")
	private String couponName;

	@Schema(description = "状态：0-正常；1-禁用")
	private String status;

	@Schema(description = "剩余数量")
	private Integer remainNum;

	@Schema(description = "领取截止时间")
	private LocalDateTime receiveEndedAt;

}
