package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "C端秒杀商品VO")
public class AppSeckillGoodsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "秒杀商品ID")
	private String id;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "场次ID")
	private String sessionId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "商品名称")
	private String goodsName;

	@Schema(description = "商品图片")
	private String goodsImage;

	@Schema(description = "原价")
	private BigDecimal originalPrice;

	@Schema(description = "秒杀价")
	private BigDecimal seckillPrice;

	@Schema(description = "秒杀库存")
	private Integer seckillStock;

	@Schema(description = "已售数量")
	private Integer soldCount;

	@Schema(description = "每人限购数")
	private Integer limitPerUser;

	@Schema(description = "剩余库存")
	private Integer remainingStock;
}