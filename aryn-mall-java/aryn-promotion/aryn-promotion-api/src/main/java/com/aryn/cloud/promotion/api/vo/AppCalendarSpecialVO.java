package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端档期特供 VO（档期内限量特供商品）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "档期特供VO")
public class AppCalendarSpecialVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "特供活动ID")
	private String activityId;

	@Schema(description = "特供活动名称")
	private String activityName;

	@Schema(description = "归属档期ID")
	private String calendarId;

	@Schema(description = "归属档期名称")
	private String calendarName;

	@Schema(description = "特供截止时间")
	private LocalDateTime endTime;

	@Schema(description = "活动级限量（件数；null=不限）")
	private Integer totalQuota;

	@Schema(description = "剩余限量（件数；totalQuota 为 null 时不限量）")
	private Integer remainingQuota;

	@Schema(description = "特供商品明细")
	private List<SpecialItem> items;

	@Data
	@Schema(description = "档期特供商品项")
	public static class SpecialItem implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "商品名（楼层卡片展示，远程回填；失败为 null 前端兜底 skuId）")
		private String spuName;

		@Schema(description = "商品图（SKU 级图，楼层卡片展示）")
		private String picUrl;

		@Schema(description = "特供价")
		private BigDecimal price;

		@Schema(description = "当前售价（划线基准）")
		private BigDecimal originalPrice;

		@Schema(description = "每人限购件数（0/null=不限）")
		private Integer perUserLimit;

	}

}
