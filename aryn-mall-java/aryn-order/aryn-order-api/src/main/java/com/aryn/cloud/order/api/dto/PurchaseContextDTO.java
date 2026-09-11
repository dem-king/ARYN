package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 购买上下文 DTO
 *
 * <p>结算与下单共用的购买上下文契约：购买场景、船舶和靠港计划、购物车类型。
 * 购买场景取值见 PurchaseSceneEnum；cartType 取值 1 普通购物车 / 2 共享购物车。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "购买上下文DTO")
public class PurchaseContextDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 普通购物车 */
	public static final String CART_TYPE_NORMAL = "1";

	/** 共享购物车 */
	public static final String CART_TYPE_SHARED = "2";

	@Schema(description = "购买场景：1.海员个人购买；2.船供采购")
	private String purchaseScene;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "靠港计划ID")
	private String vesselCallId;

	@Schema(description = "购物车类型：1.普通购物车；2.共享购物车")
	private String cartType;

}
