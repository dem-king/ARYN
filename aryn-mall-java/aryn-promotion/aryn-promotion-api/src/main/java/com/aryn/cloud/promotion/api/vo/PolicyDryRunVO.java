package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 价格政策 dry-run 预检结果（发布前冲突/倒挂检索，2026-11-14 B2B）。
 *
 * <p>verdict 口径（按严重度排序，多标签可并存）：
 * <ul>
 *   <li>OK：按当前配置将正常生效；</li>
 *   <li>INVERTED_SALES：协议价 ≥ 当前售价——引擎视为倒挂不落价，该 SKU 公开价格层继续生效；</li>
 *   <li>SHADOWED：同 SKU 存在更高 priority 的其他生效政策，或同级但更低价格——本政策对该 SKU 不会生效；</li>
 *   <li>SHADOWS_OTHER：本政策会压住同 SKU 的其他生效政策（以本政策为准）；</li>
 *   <li>COST_RISK：协议价低于成本价——让利击穿成本，运营自行决策（不阻断发布）。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/11/15
 */
@Data
@Schema(description = "价格政策dry-run结果VO")
public class PolicyDryRunVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "政策ID")
	private String policyId;

	@Schema(description = "政策名称")
	private String policyName;

	@Schema(description = "逐 SKU 预检结果")
	private List<Item> items = new ArrayList<>();

	@Data
	@Schema(description = "dry-run 逐 SKU 结果")
	public static class Item implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "商品名（远程回填，失败为空）")
		private String spuName;

		@Schema(description = "当前售价")
		private BigDecimal salesPrice;

		@Schema(description = "本政策折算后的协议价")
		private BigDecimal policyPrice;

		@Schema(description = "成本价（C 端脱敏字段，管理端预检专用）")
		private BigDecimal costPrice;

		@Schema(description = "预检结论：OK / INVERTED_SALES / SHADOWED / SHADOWS_OTHER / COST_RISK（可多值逗号分隔）")
		private String verdict;

		@Schema(description = "冲突/压制的对方政策名（SHADOWED 或 SHADOWS_OTHER 时有值）")
		private String counterpartyPolicyName;

	}

}
