
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 创建订单后事件监听
 *
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderCreateAfterEventListener {

	private final RocketMQTemplate rocketMQTemplate;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	private final IShoppingCartService shoppingCartService;

	private final IOrderConfigService orderConfigService;

	/**
	 * 清除购物车
	 * @param event
	 */
	@Async
	@EventListener(ArynOrderCreateAfterEvent.class)
	public void clearShoppingCartEventListener(ArynOrderCreateAfterEvent event) {
		if (!MallOrderConstants.ORDER_CREATE_WAY_1.equals(event.getCreateWay())) {
			return;
		}
		final OrderInfo orderInfo = event.getOrderInfo();
		final List<OrderItemEntity> orderItemEntityList = event.getOrderItemEntityList();
		shoppingCartService.clear(orderInfo.getUserId(),
				orderItemEntityList.stream()
					.map(OrderItemEntity::getSpuId)
					.distinct() // 去重，如果有可能有重复的商品ID
					.toList());
	}

	/**
	 * rocketmq 延迟消息 30分钟取消订单
	 * @param event
	 */
	@Async
	@EventListener(ArynOrderCreateAfterEvent.class)
	public void sendMqEventListener(ArynOrderCreateAfterEvent event) {
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)) {
			return;
		}

		int level = StringUtils.hasText(orderConfig.getOrderCancelTimeout())
				? Integer.parseInt(orderConfig.getOrderCancelTimeout()) : RocketMqConstants.ORDER_CANCEL_LEVEL;
		final OrderInfo orderInfo = event.getOrderInfo();
		OrderConsumerDTO orderConsumerDTO = new OrderConsumerDTO();
		orderConsumerDTO.setOrderId(orderInfo.getId());
		orderConsumerDTO.setTenantId(ArynTenantContextHolder.getTenantId());
		orderConsumerDTO.setOrderNo(orderInfo.getOrderNo());
		orderConsumerDTO.setUserId(orderInfo.getUserId());
		rocketMQTemplate.syncSend(RocketMqConstants.ORDER_CANCEL_TOPIC, new GenericMessage<>(orderConsumerDTO),
				RocketMqConstants.TIME_OUT, level);
	}

	/**
	 * TODO 发送微信小程序模板消息
	 * @param event
	 */
	@Async
	@EventListener(ArynOrderCreateAfterEvent.class)
	public void sendWxMsgEventListener(ArynOrderCreateAfterEvent event) {
		log.info("sendWxMsgEventListener");
	}

	/**
	 * 修改使用的优惠券状态
	 * @param event
	 */
	@Async
	@EventListener(ArynOrderCreateAfterEvent.class)
	public void updateUserCouponStatusEventListener(ArynOrderCreateAfterEvent event) {
		final OrderInfo orderInfo = event.getOrderInfo();
		if (!StringUtils.hasText(orderInfo.getCouponUserId())) {
			return;
		}
		CouponUserReqDTO couponUserReqDTO = new CouponUserReqDTO();
		couponUserReqDTO.setId(orderInfo.getCouponUserId());
		couponUserReqDTO.setCouponUserStatusEnum(CouponUserStatusEnum.STATUS_3);
		remoteCouponUserService.updateCouponUserStatus(couponUserReqDTO);

	}

}
