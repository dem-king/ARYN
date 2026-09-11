
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 下单DTO
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Data
@Schema(description = "下单DTO")
public class CreateOrderDTO {

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "配送方式：1.普通快递；2.上门自提；3.商城配送；4.公司港口/船舶内部配送")
	@NotBlank(message = "配送方式不能为空")
	@Pattern(regexp = "[1-4]", message = "配送方式不合法")
	private String deliveryWay;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付")
	private String paymentType;

	@Schema(description = "交易类型")
	private String tradeType;

	@Schema(description = "用户收货地址")
	private String userAddressId;

	@Schema(description = "订单创建方式：1.购物车下单；2.普通购买下单")
	@NotBlank(message = "订单创建方式不能为空")
	@Pattern(regexp = "[12]", message = "订单创建方式不合法")
	private String createWay;

	@Schema(description = "订单商品sku集合")
	@Valid
	@NotEmpty(message = "订单商品不能为空")
	private List<CreateOrderSkuReqDTO> skuReqList;

	@Schema(description = "应用ID")
	private String appId;

	@Schema(description = "openId")
	private String openId;

	@Schema(description = "用户优惠券id")
	private String couponUserId;

	@Size(max = 64, message = "请求幂等号长度不能超过64")
	@Schema(description = "客户端请求幂等号")
	private String requestId;

	@Schema(description = "订单备注")
	private String remark;

	@Schema(description = "购买场景：1.海员个人购买；2.船供采购")
	@Pattern(regexp = "[12]", message = "购买场景不合法")
	private String purchaseScene;

	@Schema(description = "配送船舶ID（内部配送必填）")
	private String vesselId;

	@Schema(description = "靠港计划ID（内部配送必填）")
	private String vesselCallId;

	@Schema(description = "收货人姓名（内部配送）")
	private String recipientName;

	@Schema(description = "收货人电话（内部配送）")
	private String recipientPhone;

	@Schema(description = "船上代理/经办人姓名（内部配送）")
	private String agentName;

	@Schema(description = "船上代理/经办人电话（内部配送）")
	private String agentPhone;

}
