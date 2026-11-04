package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品专区标签批量保存请求：按 SPU 勾选后统一打/取消会员与出清标签。
 *
 * <p>标签字段为 null 表示「不修改」；显式 false 会清空对应专区的标签与规则，
 * 并把 SKU 级覆盖一并清空（避免「SPU 已取消、SKU 仍命中」）。
 *
 * <p>批量填写规则时默认保留各 SKU 已有精调，只有 {@code overwriteSkuRules=true}
 * 才会清空 SKU 覆盖、统一按本次规则生效。
 *
 * <p>批量不提供「会员价下限」：下限按商品成本自动兜底，避免不同成本商品共用一个人工值；
 * 个别商品需要特殊底价时，走单商品精调接口（GoodsZoneTagSaveDTO）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "商品专区标签批量保存请求")
public class GoodsZoneTagBatchDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
	private java.util.List<String> spuIds;

	@Schema(description = "会员专区动作（预检/批量通用）：ADD 加入·更新 / REMOVE 移出 / NONE 不修改；与 memberTag 二选一")
	private String memberAction;

	@Schema(description = "会员专区标签：true 打标 / false 取消 / null 不修改")
	private Boolean memberTag;

	@Schema(description = "商品级会员折扣率，如 0.9000；空=用等级折扣率")
	private BigDecimal memberDiscountRate;

	@Schema(description = "会员固定价；与按率算价取低")
	private BigDecimal memberPriceFixed;

	@Schema(description = "低价捡漏动作（预检/批量通用）：ADD 加入·更新 / REMOVE 移出 / NONE 不修改；与 clearanceTag 二选一")
	private String clearanceAction;

	@Schema(description = "捡漏专区标签：true 打标 / false 取消 / null 不修改")
	private Boolean clearanceTag;

	@Schema(description = "出清固定价，与 clearanceDiscountRate 二选一")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率（对 originalPrice 计算）")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间")
	private LocalDateTime clearanceExpireTime;

	/**
	 * 是否用本次填写的规则统一覆盖 SKU 级精调（默认 false，保留各规格已配置的价格）。
	 */
	@Schema(description = "是否清空并覆盖已配置的 SKU 级规则")
	private Boolean overwriteSkuRules;

}
