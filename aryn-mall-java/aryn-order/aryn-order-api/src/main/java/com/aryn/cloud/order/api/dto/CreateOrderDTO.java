
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
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

	@Schema(description = "配送方式：1.普通快递；2.上门自提；3.公司配送（岸上）；4.公司配送（到船）")
	@NotBlank(message = "配送方式不能为空")
	@Pattern(regexp = "[1-4]", message = "配送方式不合法")
	private String deliveryWay;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付；3.货到付款（仅公司配送）")
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

	@Size(max = 64, message = "拼团记录ID长度不能超过64")
	@Schema(description = "拼团记录ID（拼团下单时携带，服务端按拼团价成交；普通下单为空）")
	private String groupBuyRecordId;

	@Size(max = 64, message = "请求幂等号长度不能超过64")
	@Schema(description = "客户端请求幂等号")
	private String requestId;

	@Schema(description = "订单备注")
	private String remark;

	@Schema(description = "确认页预期应付金额（元）：服务端重算不一致时拒绝成单，防止活动额度变化后静默涨价")
	private BigDecimal expectedPaymentPrice;

	@Schema(description = "使用积分数量（积分抵现；0或空表示不使用）")
	private Integer usePoints;

	@Schema(description = "是否使用全部可用积分（确认页开关；优先于 usePoints）")
	private Boolean useMaxPoints;

	@Schema(description = "购买场景：1.海员个人购买；2.船供采购")
	@Pattern(regexp = "[12]", message = "购买场景不合法")
	private String purchaseScene;

	@Schema(description = "配送船舶ID（到船配送必填）")
	private String vesselId;

	@Schema(description = "靠港计划ID（到船配送必填）")
	private String vesselCallId;

	@Schema(description = "收货人姓名（到船配送）")
	private String recipientName;

	@Schema(description = "收货人电话（到船配送）")
	private String recipientPhone;

	@Schema(description = "船上代理/经办人姓名（到船配送）")
	private String agentName;

	@Schema(description = "船上代理/经办人电话（到船配送）")
	private String agentPhone;

}
