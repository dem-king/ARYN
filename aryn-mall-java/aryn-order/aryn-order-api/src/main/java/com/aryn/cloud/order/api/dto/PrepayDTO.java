
package com.aryn.cloud.order.api.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 预支付DTO
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Data
@Schema(description = "预支付DTO")
public class PrepayDTO {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "配送方式：1.普通快递；2.上门自提；")
	private String deliveryWay;

	@Schema(description = "订单单号")
	private String orderNo;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付")
	private String paymentType;

	@Schema(description = "交易类型")
	private String tradeType;

	@Schema(description = "同步跳转地址，仅支持http/https")
	private String returnUrl;

	@Schema(description = "用户付款中途退出返回商户网站的地址")
	private String quitUrl;

}
