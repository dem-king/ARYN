package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史补给单复用结果。
 *
 * <p>复用是**结构复制**：逐条把源单明细写进目标车，数量按
 * 「计划量 → 核定数量 → 申请数量」依次回落（计划量代表上次实际要采多少，
 * 是最贴近"照上次再来一份"的口径）。已采量不复制 —— 那是上一轮的既成事实，
 * 新一轮的已采量从 0 开始，否则进度条一上来就是满的。
 *
 * <p>明细按 SKU 合并：源单可能因「按人拆行」出现同一 SKU 多行，复用时归属
 * 已收敛为当前操作者一人，再保留多行只会让清单变乱。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "历史补给单复用结果")
public class SharedCartReuseVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "复用后进入的购物车ID")
	private String cartId;

	@Schema(description = "购物车编号")
	private String cartNo;

	@Schema(description = "是否并入该船已有的进行中购物车（true 表示不是新建的）")
	private Boolean adoptedExisting;

	@Schema(description = "成功复用的明细项数（按 SKU 合并后）")
	private Integer reusedCount = 0;

	@Schema(description = "跳过的明细项数（数量不可用时跳过）")
	private Integer skippedCount = 0;

	@Schema(description = "跳过明细")
	private List<Skipped> skipped = new ArrayList<>();

	/**
	 * 被跳过的源明细。
	 */
	@Data
	@Schema(description = "复用跳过明细")
	public static class Skipped implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "跳过原因（面向用户可读）")
		private String reason;

		public Skipped() {
		}

		public Skipped(String skuId, String reason) {
			this.skuId = skuId;
			this.reason = reason;
		}

	}

}
