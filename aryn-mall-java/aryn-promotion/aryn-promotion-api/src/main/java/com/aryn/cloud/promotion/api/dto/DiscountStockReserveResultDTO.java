package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 折扣库存占用结果（营销域 → 下单域的远程出参）。
 *
 * <p>用结果对象而不是抛异常：Dubbo 会把跨进程异常包装成 RpcException，
 * 下单侧只认 ArynBusinessException 时会被当成「营销服务不可用」而静默放行，
 * 超卖拦截就失效了。显式返回成功/失败，由下单侧决定抛业务异常。
 */
@Data
@Schema(description = "折扣库存占用结果")
public class DiscountStockReserveResultDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "是否占用成功")
	private boolean success;

	@Schema(description = "失败时的 SKU ID")
	private String failedSkuId;

	@Schema(description = "失败提示（可直接展示给用户）")
	private String message;

	public static DiscountStockReserveResultDTO ok() {
		DiscountStockReserveResultDTO result = new DiscountStockReserveResultDTO();
		result.setSuccess(true);
		return result;
	}

	public static DiscountStockReserveResultDTO fail(String skuId, String message) {
		DiscountStockReserveResultDTO result = new DiscountStockReserveResultDTO();
		result.setSuccess(false);
		result.setFailedSkuId(skuId);
		result.setMessage(message);
		return result;
	}

}