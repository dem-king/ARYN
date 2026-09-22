package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车批量加购结果。
 *
 * <p>设计为**部分成功**语义：批量加购常见于「勾选清单里的多项一次加入」，
 * 若其中一项缺货就整批回滚，用户需要反复试错才能找出是哪一项。
 * 因此逐项独立提交，返回成功/失败清单，前端可提示"已加入 8 项，2 项失败"。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "购物车批量加购结果")
public class ShoppingCartBatchAddVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "提交的总项数")
	private Integer requestedCount = 0;

	@Schema(description = "成功加入的项数")
	private Integer addedCount = 0;

	@Schema(description = "失败的项数")
	private Integer failedCount = 0;

	@Schema(description = "失败明细（成功项不出现在此列表）")
	private List<Failure> failures = new ArrayList<>();

	/**
	 * 记录一项失败原因的条目。
	 */
	@Data
	@Schema(description = "批量加购失败明细")
	public static class Failure implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "请求数量")
		private Integer quantity;

		@Schema(description = "失败原因（面向用户可读）")
		private String reason;

		public Failure() {
		}

		public Failure(String skuId, Integer quantity, String reason) {
			this.skuId = skuId;
			this.quantity = quantity;
			this.reason = reason;
		}

	}

}
