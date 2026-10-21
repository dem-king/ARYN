package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端新人专享楼层 VO。
 *
 * <p>价格策略：接口固定返回新人价（楼层展示用），资格判定在下单/结算链路做，
 * 不在本接口按人出价——避免用户维度缓存与免登态复杂性；未登录前端本地渲染原价兜底。
 *
 * @author aryn
 * @since 2026/10/13
 */
@Data
@Schema(description = "C端新人专享楼层VO")
public class AppNewbieFloorVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "注册时效（天），null/空表示不限")
	private Integer registerWithinDays;

	@Schema(description = "每人每 SKU 限购件数（固定 1）")
	private Integer limitPerUser;

	@Schema(description = "活动结束时间")
	private LocalDateTime endTime;

	@Schema(description = "新人价商品列表")
	private List<NewbieGoodsVO> goodsList;

	@Data
	@Schema(description = "新人价商品项")
	public static class NewbieGoodsVO implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "商品名称")
		private String goodsName;

		@Schema(description = "商品图片")
		private String goodsImage;

		@Schema(description = "新人专享价")
		private BigDecimal newbiePrice;

		@Schema(description = "划线原价（当前售价）")
		private BigDecimal originalPrice;

	}

}
