package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页「今日补给单」卡片的聚合视图。
 *
 * <p>首页只展示一张卡，但需要「进行中的购物车 / 项数 / 参与人数 / 预估金额 / 明细预览」
 * 五类信息。若由前端分别调 {@code /my} + {@code /{id}/items} + 商品批量查询，
 * 首屏会多出串行请求（装修校验器 {@code MAX_REQUESTS=10} 的预算也吃紧），故在服务端一次组装。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "共享购物车聚合摘要（首页补给单卡片）")
public class SharedCartSummaryVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 卡片文案里最多列几个商品，超出以省略号收尾 */
	public static final int MAX_PREVIEW_ITEMS = 3;

	@Schema(description = "进行中的共享购物车；无进行中的购物车时为 null")
	private SharedCartVO cart;

	@Schema(description = "有效明细项数（不含已移除/已确认移出）")
	private Integer itemCount = 0;

	@Schema(description = "参与成员数")
	private Integer memberCount = 0;

	@Schema(description = "预估金额（SKU 当前售价 × 申请数量；未含促销，实付以结算页为准）")
	private BigDecimal totalAmount = BigDecimal.ZERO;

	@Schema(description = "明细预览（最多 3 项）")
	private List<SummaryItem> previewItems = new ArrayList<>();

	@Schema(description = "明细是否超过预览上限")
	private Boolean previewTruncated = Boolean.FALSE;

	/**
	 * 明细预览的一行。
	 */
	@Data
	@Schema(description = "摘要明细项")
	public static class SummaryItem implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "明细ID")
		private String itemId;

		@Schema(description = "商品SPU ID")
		private String spuId;

		@Schema(description = "商品SKU ID")
		private String skuId;

		@Schema(description = "商品名称（商品域查询失败时为 null，前端回落 SKU ID）")
		private String spuName;

		@Schema(description = "规格信息")
		private String specsInfo;

		@Schema(description = "用量（采购单位）")
		private Integer quantity;

		@Schema(description = "图片地址")
		private String picUrl;

		@Schema(description = "该行小计金额")
		private BigDecimal amount;

	}

}
