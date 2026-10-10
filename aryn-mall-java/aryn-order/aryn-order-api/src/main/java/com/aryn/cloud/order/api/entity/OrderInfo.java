
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单信息
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:28
 */
@Data
@Schema(description = "订单信息")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "order_info")
public class OrderInfo extends Model<OrderInfo> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户主键")
	private String userId;

	@Schema(description = "配送方式：1.普通快递；2.上门自提；")
	private String deliveryWay;

	@Schema(description = "订单单号")
	private String orderNo;

	@Schema(description = "客户端请求幂等号")
	private String requestId;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付")
	private String paymentType;

	@Schema(description = "交易类型")
	private String tradeType;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "支付状态：0.未支付；1.已支付;")
	private String payStatus;

	@Schema(description = "订单状态")
	private String status;

	@Schema(description = "评价状态：0.待评价；1.已平价;")
	private String appraiseStatus;

	@Schema(description = "订单总金额（元）")
	private BigDecimal totalPrice;

	@Schema(description = "运费（元）")
	private BigDecimal freightPrice;

	@Schema(description = "优惠券优惠金额（元）")
	private BigDecimal couponPrice;

	@Schema(description = "营销整单优惠金额（阶梯价计入明细单价，整船优惠在此汇总）")
	private BigDecimal promoPrice;

	@Schema(description = "命中营销活动明细（结算/详情展示）")
	@TableField(exist = false)
	private List<com.aryn.cloud.promotion.api.vo.PromotionCalculationVO.ActivityDetail> promotionDetails;

	@Schema(description = "会员折扣优惠金额（元）")
	private BigDecimal memberDiscountPrice;

	@Schema(description = "下单时会员积分倍率")
	private BigDecimal pointsMultiplier;

	@Schema(description = "支付金额（总金额-优惠券优惠金额+运费 = 支付金额）")
	private BigDecimal paymentPrice;

	@Schema(description = "实收金额（货到付款确认收款时登记；NULL=未确认）")
	private BigDecimal actualPayPrice;

	@Schema(description = "付款凭证快照 JSON 数组 [{materialId, materialUrl}]（确认收款时写入）")
	private String payVouchers;

	@Schema(description = "付款时间")
	private LocalDateTime paymentTime;

	@Schema(description = "发货时间")
	private LocalDateTime deliverTime;

	@Schema(description = "取消时间")
	private LocalDateTime cancelTime;

	@Schema(description = "收货时间")
	private LocalDateTime receiverTime;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "微信支付单号")
	private String transactionId;

	@Schema(description = "用户优惠券id")
	private String couponUserId;

	@Schema(description = "拼团记录ID（拼团单关联开团/参团记录，用于成团判定与取消释放；普通订单为NULL）")
	private String groupBuyRecordId;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "收货人姓名")
	private String recipientName;

	@Schema(description = "收货人电话")
	private String recipientPhone;

	@Schema(description = "省")
	private String recipientProvince;

	@Schema(description = "市")
	private String recipientCity;

	@Schema(description = "区/县")
	private String recipientArea;

	@Schema(description = "省编码")
	private String recipientProvinceCode;

	@Schema(description = "市编码")
	private String recipientCityCode;

	@Schema(description = "区/县编码")
	private String recipientAreaCode;

	@Schema(description = "详细地址")
	private String recipientAddress;

	@Schema(description = "购买场景：1海员个人购买 2船供采购")
	private String purchaseScene;

	@Schema(description = "配送船舶ID快照")
	private String vesselId;

	@Schema(description = "配送船舶名称快照")
	private String vesselName;

	@Schema(description = "靠港计划ID快照")
	private String vesselCallId;

	@Schema(description = "港口编码快照")
	private String portCode;

	@Schema(description = "港口名称快照")
	private String portName;

	@Schema(description = "泊位快照")
	private String berth;

	@Schema(description = "配送时间窗开始快照")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束快照")
	private LocalDateTime deliveryWindowEnd;

	@Schema(description = "船上代理/经办人姓名快照")
	private String agentName;

	@Schema(description = "船上代理/经办人电话快照")
	private String agentPhone;

	@Schema(description = "来源共享购物车ID")
	private String sharedCartId;

	@Schema(description = "应用ID")
	private String appId;

	@Schema(description = "openId")
	private String openId;

	@Schema(description = "子订单集合")
	@TableField(exist = false)
	private List<OrderItemEntity> orderItemList;

	@Schema(description = "搜索关键字")
	@TableField(exist = false)
	private String keyword;

	@Schema(description = "订单ID集合（管理端导出所选订单）")
	@TableField(exist = false)
	private List<String> ids;

	@Schema(description = "支付时间范围[开始时间,结束时间]")
	@TableField(exist = false)
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime[] paymentQueryTimes;

	@Schema(description = "发货单")
	@TableField(exist = false)
	private OrderDelivery orderDelivery;

	@Schema(description = "配送任务（商城配送/内部配送订单详情回填，快递订单为空）")
	@TableField(exist = false)
	private DeliveryTask deliveryTask;

	@Schema(description = "支付时限（分钟）：按订单配置的超时取消延迟级别换算，仅待付款订单下发，供 C 端提示文案使用")
	@TableField(exist = false)
	private Integer payTimeoutMinutes;

	@Schema(description = "货物是否已送达：已收货(完成)按已送达，配送中按 delivery_task 送达/签收判定，"
			+ "快递按发货单签收判定。货到付款确认收款以此为前提")
	@TableField(exist = false)
	private Boolean delivered;

}
