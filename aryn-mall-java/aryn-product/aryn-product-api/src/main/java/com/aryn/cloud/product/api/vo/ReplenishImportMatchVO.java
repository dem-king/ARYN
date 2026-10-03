package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 补给单导入行匹配结果（商品域 → 订单域）。
 *
 * <p>{@code matchType} 的取值与前端报告口径一一对应：
 * <ul>
 *   <li>{@code CODE} —— 编码唯一命中；</li>
 *   <li>{@code NAME} —— 品名命中且没有歧义；</li>
 *   <li>{@code AMBIGUOUS} —— 命中多个 SKU，必须人工补选，**不自动入单**；</li>
 *   <li>{@code NONE} —— 未命中，人工补选。</li>
 * </ul>
 *
 * <p>{@code skuStatus} / {@code spuStatus} 会如实返回「已下架」的商品：
 * 订单域的 {@code RemoteGoodsSkuService} 会静默过滤下架数据，若这里也过滤，
 * 报告会把它误报成「未匹配」，用户永远补不到正确商品。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单导入行匹配结果")
public class ReplenishImportMatchVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "行号（从1开始，回显用）")
	private Integer rowNo;

	@Schema(description = "匹配方式：CODE编码 / NAME品名 / AMBIGUOUS多规格待选 / NONE未命中")
	private String matchType;

	@Schema(description = "匹配到的SPU ID（未命中时为空）")
	private String spuId;

	@Schema(description = "匹配到的SKU ID（未命中/歧义时为空）")
	private String skuId;

	@Schema(description = "商品名")
	private String name;

	@Schema(description = "规格描述（SKU 规格值拼接，用于「规格变更」比对）")
	private String spec;

	@Schema(description = "售价")
	private BigDecimal salesPrice;

	@Schema(description = "SKU 库存")
	private Integer stock;

	@Schema(description = "最小起订量（船供包装资料下线后恒空，订单域按 1 兜底）")
	private Integer moq;

	@Schema(description = "数量步长（船供包装资料下线后恒空，订单域按 1 兜底）")
	private Integer stepQty;

	@Schema(description = "SKU 状态：0正常 1下架")
	private String skuStatus;

	@Schema(description = "SPU 状态：1上架 0下架")
	private String spuStatus;

	@Schema(description = "候选明细（歧义时给出，供人工补选；最多 20 条）")
	private List<Candidate> candidates;

	@Schema(description = "候选/歧义明细分项")
	@Data
	public static class Candidate implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "商品名")
		private String name;

		@Schema(description = "规格描述")
		private String spec;

		@Schema(description = "售价")
		private BigDecimal salesPrice;

		@Schema(description = "库存")
		private Integer stock;

		@Schema(description = "SKU 状态：0正常 1下架")
		private String skuStatus;

		@Schema(description = "SPU 状态：1上架 0下架")
		private String spuStatus;

	}

}
