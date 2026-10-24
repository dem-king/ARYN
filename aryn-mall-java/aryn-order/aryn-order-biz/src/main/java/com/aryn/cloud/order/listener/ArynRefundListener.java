
package com.aryn.cloud.order.listener;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.user.api.remote.RemotePointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
@RocketMQMessageListener(topic = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
		consumerGroup = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC)
public class ArynRefundListener implements RocketMQListener<String> {

	private final IOrderRefundService orderRefundService;

	private final IOrderItemService orderItemService;

	private final IOrderInfoService orderInfoService;

	private final RocketMQTemplate rocketMQTemplate;

	private final com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	@DubboReference
	private final RemotePointsService remotePointsService;

	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	@Transactional(rollbackFor = Exception.class)
	public void onMessage(String message) {
		ArynTenantContextHolder.removeTenantId();
		final JSONObject msg = JSONObject.parseObject(message);
		final String tenantId = msg.getString(PayConstants.TENANT_ID);
		if (!StringUtils.hasText(tenantId)) {
			log.warn("tenantId empty! ");
			return;
		}
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			final String refundTradeMo = msg.getString(PayConstants.REFUND_TRADE_NO);
			if (!StringUtils.hasText(refundTradeMo)) {
				log.warn("orderNo empty! ");
				return;
			}
			OrderRefund orderRefund = orderRefundService
				.getOne(Wrappers.<OrderRefund>lambdaQuery().eq(OrderRefund::getRefundTradeNo, refundTradeMo));
			if (null == orderRefund) {
				log.warn("order not found! orderNo: " + refundTradeMo);
				return;
			}
			OrderItemEntity orderItemEntity = orderItemService.getById(orderRefund.getOrderItemId());
			if (null == orderItemEntity) {
				log.warn("order item not found! orderItemId: " + orderRefund.getOrderItemId());
				return;
			}
			OrderInfo orderInfo = orderInfoService.getById(orderRefund.getOrderId());
			if (Objects.isNull(orderInfo)) {
				log.warn("order not found! orderId: " + orderRefund.getOrderId());
				return;
			}
			if (OrderArrivalStatusEnum.REFUNDING.getCode().equals(orderRefund.getArrivalStatus())) {
				orderRefund.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
				if (!orderRefundService.updateById(orderRefund)) {
					throw new ArynBusinessException("退款状态更新失败，请重试");
				}
			}
			else if (!OrderArrivalStatusEnum.REFUND_SUCCESS.getCode().equals(orderRefund.getArrivalStatus())) {
				log.warn("refund status is not refundable, refundTradeNo: {}", refundTradeMo);
				return;
			}
			if (!OrderItemStatusEnum.REFUNDED.getCode().equals(orderItemEntity.getStatus())) {
				orderItemEntity.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
				if (!orderItemService.updateById(orderItemEntity)) {
					throw new ArynBusinessException("订单商品退款状态更新失败，请重试");
				}
			}

			// 买赠赠品（gift_flag=1，0 元）不走现金退款，不能参与「是否全部退款」判定：
			// 否则含赠品的订单永远判定不了全退，积分/优惠券不返还、订单也不会关闭
			List<OrderItemEntity> orderItemEntityList = orderItemService.list(Wrappers.<OrderItemEntity>lambdaQuery()
					.eq(OrderItemEntity::getOrderId, orderRefund.getOrderId())
					.ne(OrderItemEntity::getId, orderItemEntity.getId())
					.ne(OrderItemEntity::getStatus, OrderItemStatusEnum.REFUNDED.getCode()))
				.stream()
				.filter(item -> !com.aryn.cloud.common.core.constant.CommonConstants.YES.equals(item.getGiftFlag()))
				.toList();

			OrderRefundSuccessEvent orderRefundSuccessEvent = new OrderRefundSuccessEvent();
			BeanUtils.copyProperties(orderRefund, orderRefundSuccessEvent);
			orderRefundSuccessEvent.setPaymentType(orderInfo.getPaymentType());
			orderRefundSuccessEvent.setRefundNo(orderRefund.getId());
			orderRefundSuccessEvent.setRefundBaseAmount(refundBaseAmount(orderRefund, orderItemEntity));

			OrderItemRefundSuccessEvent orderItemRefundSuccessEvent = new OrderItemRefundSuccessEvent();
			BeanUtils.copyProperties(orderItemEntity, orderItemRefundSuccessEvent);
			orderRefundSuccessEvent.setOrderItem(orderItemRefundSuccessEvent);
			// 判断是否全部完成退款
			boolean fullRefund = CollectionUtils.isEmpty(orderItemEntityList);
			if (fullRefund) {
				if (!OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())) {
					OrderInfo info = new OrderInfo();
					info.setId(orderRefund.getOrderId());
					info.setStatus(OrderStatusEnum.CANCELED.getCode());
					if (!orderInfoService.updateById(info)) {
						throw new ArynBusinessException("订单退款完成状态更新失败，请重试");
					}
				}
				if (com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
						|| com.aryn.cloud.order.api.constant.MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay())) {
					try {
						deliveryTaskService.cancelByOrderId(orderRefund.getOrderId());
					} catch (Exception e) {
						log.warn("退款完成关闭配送任务失败: {}", e.getMessage());
					}
				}
				orderRefundSuccessEvent.setCouponUserId(orderInfo.getCouponUserId());
			}
			// 积分返还放在链路最后（bizNo 幂等，MQ 重投安全）：
			// 部分退款按明细分摊比例返还、全退补齐尾差，两条路合计恰好 pointsUsed、不超返
			if (fullRefund) {
				refundRemainPointsOnFullRefund(orderInfo, orderRefund, orderItemEntity);
			}
			else {
				refundPointsOnPartialRefund(orderInfo, orderRefund, orderItemEntity);
			}
			TransactionalMqUtils.sendAfterCommit(() -> rocketMQTemplate.syncSend(
				RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
				new GenericMessage<>(orderRefundSuccessEvent), RocketMqConstants.TIME_OUT));
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	/**
	 * 退款基数：退款金额扣除运费（运费不走商品退款链路），最低 0。
	 */
	private BigDecimal refundBaseAmount(OrderRefund refund, OrderItemEntity orderItem) {
		BigDecimal refundAmount = refund.getRefundAmount() == null ? BigDecimal.ZERO : refund.getRefundAmount();
		BigDecimal freightAmount = orderItem.getFreightPrice() == null ? BigDecimal.ZERO : orderItem.getFreightPrice();
		return refundAmount.subtract(freightAmount).max(BigDecimal.ZERO);
	}

	/**
	 * 部分退款积分返还：按明细积分分摊比例向下取整计算。
	 *
	 * <p>返还积分 = floor(pointsUsed × item.pointsPrice / pointsDeductPrice)。
	 * 选 pointsPrice（积分抵扣分摊额）而非实付金额做比例分母：下单时抵扣金额按
	 * 明细商品应付比例分摊到 {@code order_item.pointsPrice}（Σ明细 = 订单抵扣额），
	 * 因此 pointsPrice 就是「落在该明细上的抵扣份额」，按它比例返还最精确；
	 * 而实付金额含会员折扣/优惠券等多重干扰，且黑名单明细实付不为 0 但 pointsPrice 恒为 0，
	 * 用实付比例会给未抵扣的明细错算出返还份额。
	 *
	 * <p>floor 向下取整保证多笔部分退款累计永不超返，取整尾差由最后一笔触发全退时
	 * 统一补齐（见 {@link #refundRemainPointsOnFullRefund}）。
	 * bizNo 挂退款单号（ORDER_REFUND:{orderNo}:{refundId}），同一退款单 MQ 重投/并发
	 * 只返一次，且与全退幂等键 ORDER_REFUND:{orderNo} 天然区分，不会互相挤占。
	 */
	void refundPointsOnPartialRefund(OrderInfo orderInfo, OrderRefund orderRefund,
			OrderItemEntity orderItem) {
		if (orderInfo.getPointsUsed() == null || orderInfo.getPointsUsed() <= 0
				|| orderInfo.getPointsDeductPrice() == null
				|| orderInfo.getPointsDeductPrice().compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		BigDecimal pointsPrice = orderItem.getPointsPrice() == null ? BigDecimal.ZERO : orderItem.getPointsPrice();
		if (pointsPrice.compareTo(BigDecimal.ZERO) <= 0) {
			// 该明细未分摊积分抵扣（黑名单/不可叠活动/0 元赠品），无可返还份额
			return;
		}
		int refundPoints = BigDecimal.valueOf(orderInfo.getPointsUsed())
			.multiply(pointsPrice)
			.divide(orderInfo.getPointsDeductPrice(), 0, RoundingMode.DOWN)
			.intValueExact();
		if (refundPoints <= 0) {
			return;
		}
		String bizNo = "ORDER_REFUND:" + orderInfo.getOrderNo() + ":" + orderRefund.getId();
		remotePointsService.refundPoints(orderInfo.getUserId(), refundPoints, bizNo,
				"订单部分退款返还积分：" + orderInfo.getOrderNo());
	}

	/**
	 * 全退积分尾差补齐：返还 pointsUsed - Σ「其余已退明细按部分退口径 floor 的份额」。
	 *
	 * <p>触发全退的必是最后一笔退款的明细，此前每笔退款都已走部分退分支返还了
	 * floor 份额；此处的差额 = 当前明细份额 + 其余明细的取整损耗，一次补齐后
	 * 整单累计返还恰好等于 pointsUsed（与 Σ pointsPrice 是否等于 pointsDeductPrice
	 * 无关，数学上恒成立），单明细直退全退时退全额、不超返。
	 * bizNo 沿用 ORDER_REFUND:{orderNo}，与取消路径共用幂等键，两条路只退一次。
	 */
	void refundRemainPointsOnFullRefund(OrderInfo orderInfo, OrderRefund orderRefund,
			OrderItemEntity orderItem) {
		if (orderInfo.getPointsUsed() == null || orderInfo.getPointsUsed() <= 0
				|| orderInfo.getPointsDeductPrice() == null
				|| orderInfo.getPointsDeductPrice().compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		int remainPoints = orderInfo.getPointsUsed();
		List<OrderItemEntity> refundedItems = orderItemService.list(Wrappers.<OrderItemEntity>lambdaQuery()
			.eq(OrderItemEntity::getOrderId, orderRefund.getOrderId()));
		for (OrderItemEntity item : refundedItems) {
			// 当前明细的份额含在尾差里由本笔补齐，只扣减其余明细已按部分退返还的份额
			if (item.getId() != null && item.getId().equals(orderItem.getId())) {
				continue;
			}
			BigDecimal pointsPrice = item.getPointsPrice() == null ? BigDecimal.ZERO : item.getPointsPrice();
			if (pointsPrice.compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			remainPoints -= BigDecimal.valueOf(orderInfo.getPointsUsed())
				.multiply(pointsPrice)
				.divide(orderInfo.getPointsDeductPrice(), 0, RoundingMode.DOWN)
				.intValueExact();
		}
		if (remainPoints <= 0) {
			return;
		}
		remotePointsService.refundPoints(orderInfo.getUserId(), remainPoints,
				"ORDER_REFUND:" + orderInfo.getOrderNo(),
				"订单退款返还积分：" + orderInfo.getOrderNo());
	}

}
