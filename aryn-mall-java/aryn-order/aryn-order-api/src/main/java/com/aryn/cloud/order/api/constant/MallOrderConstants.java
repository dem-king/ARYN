
package com.aryn.cloud.order.api.constant;

/**
 * 商城订单常量
 *
 * @author 雨滴kian
 * @date 2022/6/8
 */
public class MallOrderConstants {

	/** 配送方式：1.普通快递；2.上门自提；3.商城配送 */
	public static final String DELIVERY_WAY_1 = "1";

	public static final String DELIVERY_WAY_2 = "2";

	public static final String DELIVERY_WAY_3 = "3";

	/** 支付类型：1.微信支付；2.支付宝支付; */
	public static final String PAYMENT_TYPE_0 = "0";

	public static final String PAYMENT_TYPE_1 = "1";

	public static final String PAYMENT_TYPE_2 = "2";

	/** 订单创建方式：1.购物车下单；2.普通购买下单 */
	public static final String ORDER_CREATE_WAY_1 = "1";

	/** 物流回调接口 */
	public static final String NOTIFY_LOGISTICS_URL = "/mall-order/notify/delivery?deliveryId=%s&tenantId=%s";

	/** 退货退款操作状态：2.拒绝；4.退款 */

	public static final String OPERATE_STATUS_REJECT = "2";

	public static final String OPERATE_STATUS_REFUND = "4";

}
