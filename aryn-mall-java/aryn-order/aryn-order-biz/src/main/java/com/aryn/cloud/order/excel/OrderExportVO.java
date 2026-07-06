
package com.aryn.cloud.order.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单导出VO
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@ColumnWidth(20)
public class OrderExportVO {

	@ExcelProperty("订单号")
	@ColumnWidth(30)
	private String orderNo;

	@ExcelProperty("收货人")
	private String recipientName;

	@ExcelProperty("收货电话")
	private String recipientPhone;

	@ExcelProperty("收货地址")
	@ColumnWidth(40)
	private String recipientAddress;

	@ExcelProperty("配送方式")
	private String deliveryWay;

	@ExcelProperty("支付类型")
	private String paymentType;

	@ExcelProperty("支付状态")
	private String payStatus;

	@ExcelProperty("订单状态")
	private String status;

	@ExcelProperty("商品总金额")
	private BigDecimal totalPrice;

	@ExcelProperty("运费")
	private BigDecimal freightPrice;

	@ExcelProperty("优惠券优惠")
	private BigDecimal couponPrice;

	@ExcelProperty("实付金额")
	private BigDecimal paymentPrice;

	@ExcelProperty("备注")
	@ColumnWidth(30)
	private String remark;

	@ExcelProperty("下单时间")
	@ColumnWidth(25)
	private String createTime;

	@ExcelProperty("支付时间")
	@ColumnWidth(25)
	private String paymentTime;

}