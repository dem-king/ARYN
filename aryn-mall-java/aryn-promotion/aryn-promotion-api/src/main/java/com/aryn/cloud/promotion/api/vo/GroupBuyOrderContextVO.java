package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拼团下单上下文 VO（供订单服务校验取价）
 */
@Data
public class GroupBuyOrderContextVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "拼团记录ID")
	private String recordId;

	@Schema(description = "拼团活动ID")
	private String activityId;

	@Schema(description = "拼团活动名称")
	private String activityName;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "拼团价（单价）")
	private BigDecimal groupPrice;

	@Schema(description = "原价（单价）")
	private BigDecimal originalPrice;

	@Schema(description = "拼团失效时间（超过后成团失败）")
	private LocalDateTime expireAt;
}
