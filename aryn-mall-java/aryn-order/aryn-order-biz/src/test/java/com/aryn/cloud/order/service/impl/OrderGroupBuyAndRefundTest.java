package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.pay.api.remote.RemoteRefundService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拼团价接入下单与整单退款测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderGroupBuyAndRefundTest {

	@Mock
	private com.aryn.cloud.order.mapper.OrderRefundMapper orderRefundMapper;

	@Mock
	private OrderItemMapper orderItemMapper;

	@Mock
	private OrderInfoMapper orderInfoMapper;

	private OrderPriceComputeService priceService;

	@BeforeEach
	void setUp() {
		priceService = new OrderPriceComputeService(mock(RemoteGoodsSkuService.class),
				mock(RemoteCouponUserService.class), mock(com.aryn.cloud.promotion.api.remote.RemoteDiscountService.class),
				mock(RemoteSeckillService.class), mock(RemotePromotionEngine.class));
	}

	private OrderItemEntity item(String skuId, String salesPrice, int quantity) {
		OrderItemEntity item = new OrderItemEntity();
		item.setSkuId(skuId);
		item.setSalesPrice(new BigDecimal(salesPrice));
		item.setBuyQuantity(quantity);
		return item;
	}

	/**
	 * 拼团价优先于原价落到成交基价（拼团单核心契约）。
	 */
	@Test
	void groupBuyPriceOverridesOriginalPrice() {
		OrderItemEntity orderItem = item("sku-1", "19.90", 2);
		Map<String, BigDecimal> groupBuyPrice = Map.of("sku-1", new BigDecimal("9.90"));

		priceService.orderPromotionPriceHandler(List.of(orderItem), groupBuyPrice);

		assertThat(orderItem.getSalesPrice()).isEqualByComparingTo("9.90");
		assertThat(orderItem.getTotalPrice()).isEqualByComparingTo("19.80");
	}

	/**
	 * 非拼团行不受拼团价影响，回落到原价（无秒杀/折扣时）。
	 */
	@Test
	void nonGroupBuyItemKeepsOriginalPriceWhenNoPromotion() {
		OrderItemEntity orderItem = item("sku-2", "15.00", 1);

		priceService.orderPromotionPriceHandler(List.of(orderItem), Map.of("sku-1", new BigDecimal("9.90")));

		assertThat(orderItem.getSalesPrice()).isEqualByComparingTo("15.00");
	}

	/**
	 * 拼团价高于原价时回落原价，避免拼团价买贵。
	 */
	@Test
	void groupBuyPriceAboveOriginalFallsBackToOriginal() {
		OrderItemEntity orderItem = item("sku-1", "9.90", 1);

		priceService.orderPromotionPriceHandler(List.of(orderItem), Map.of("sku-1", new BigDecimal("19.90")));

		assertThat(orderItem.getSalesPrice()).isEqualByComparingTo("9.90");
	}

	/**
	 * 无拼团上下文时行为与改造前一致（普通下单）。
	 */
	@Test
	void nullGroupBuyContextBehavesAsBefore() {
		OrderItemEntity orderItem = item("sku-1", "19.90", 1);

		priceService.orderPromotionPriceHandler(List.of(orderItem), null);

		assertThat(orderItem.getSalesPrice()).isEqualByComparingTo("19.90");
	}

	// ==================== 整单退款 ====================

	private OrderRefundServiceImpl refundService;

	private RemoteRefundService remoteRefundService;

	private IOrderConfigService orderConfigService;

	@BeforeEach
	void setUpRefund() {
		remoteRefundService = mock(RemoteRefundService.class);
		orderConfigService = mock(IOrderConfigService.class);
		com.aryn.cloud.order.api.entity.OrderConfig config = new com.aryn.cloud.order.api.entity.OrderConfig();
		config.setNotifyUrl("http://localhost/notify");
		when(orderConfigService.getConfig()).thenReturn(config);
		refundService = new OrderRefundServiceImpl(orderInfoMapper, orderItemMapper, remoteRefundService,
				orderConfigService, mock(com.aryn.cloud.order.mapper.OrderDeliveryMapper.class),
				mock(IDeliveryTaskService.class));
		ReflectionTestUtils.setField(refundService, "baseMapper", orderRefundMapper);
	}

	private OrderInfo paidOrder() {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setUserId("user-1");
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		orderInfo.setPaymentType(MallOrderConstants.PAYMENT_TYPE_1);
		orderInfo.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_2);
		orderInfo.setPaymentPrice(new BigDecimal("19.90"));
		orderInfo.setOrderNo("NO-1");
		return orderInfo;
	}

	/**
	 * 未支付订单跳过退款（取消链路自行释放占用）：不得调用支付网关。
	 */
	@Test
	void unpaidOrderIsSkipped() {
		OrderInfo orderInfo = paidOrder();
		orderInfo.setPayStatus(CommonConstants.NO);
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);

		assertThat(refundService.refundWholeOrder("order-1", "拼团失败自动退款")).isFalse();
		verify(remoteRefundService, never()).refunds(any());
	}

	/**
	 * 已取消订单跳过退款，避免重复退款。
	 */
	@Test
	void canceledOrderIsSkipped() {
		OrderInfo orderInfo = paidOrder();
		orderInfo.setStatus(OrderStatusEnum.CANCELED.getCode());
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);

		assertThat(refundService.refundWholeOrder("order-1", "拼团失败自动退款")).isFalse();
		verify(remoteRefundService, never()).refunds(any());
	}

	/**
	 * 已有退款单的明细跳过（幂等），不重复调支付网关。
	 */
	@Test
	void alreadyRefundedItemIsSkipped() {
		OrderInfo orderInfo = paidOrder();
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("item-1");
		orderItem.setOrderId("order-1");
		orderItem.setStatus(OrderItemStatusEnum.PAID.getCode());
		when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem));
		when(orderRefundMapper.selectCount(any())).thenReturn(1L);

		assertThat(refundService.refundWholeOrder("order-1", "拼团失败自动退款")).isFalse();
		verify(remoteRefundService, never()).refunds(any());
	}

	/**
	 * 未支付明细（如 0 元赠品）不参与退款。
	 */
	@Test
	void nonRefundableItemIsSkipped() {
		OrderInfo orderInfo = paidOrder();
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("item-1");
		orderItem.setOrderId("order-1");
		orderItem.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
		when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem));

		assertThat(refundService.refundWholeOrder("order-1", "拼团失败自动退款")).isFalse();
	}

	/**
	 * 订单不存在时报错，避免静默吞掉配置错误的调用。
	 */
	@Test
	void missingOrderThrows() {
		when(orderInfoMapper.selectById("order-1")).thenReturn(null);

		assertThatThrownBy(() -> refundService.refundWholeOrder("order-1", "拼团失败自动退款"))
			.isInstanceOf(ArynBusinessException.class)
			// ArynBusinessException 的 message 走 getMsg()，getMessage() 恒为 null
			.hasFieldOrPropertyWithValue("msg", "订单不存在");
	}

	/**
	 * 整单退款对有可退明细的订单发起至少一笔支付退款（真实退款链路）。
	 */
	@Test
	void refundableItemTriggersGatewayRefund() {
		OrderInfo orderInfo = paidOrder();
		when(orderInfoMapper.selectById("order-1")).thenReturn(orderInfo);
		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("item-1");
		orderItem.setOrderId("order-1");
		orderItem.setStatus(OrderItemStatusEnum.PAID.getCode());
		orderItem.setPaymentPrice(new BigDecimal("19.90"));
		when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem));
		when(orderItemMapper.selectById("item-1")).thenReturn(orderItem);
		when(orderInfoMapper.selectOne(any())).thenReturn(orderInfo);
		when(orderRefundMapper.selectCount(any())).thenReturn(0L);
		when(orderRefundMapper.insert(org.mockito.ArgumentMatchers.<OrderRefund>any())).thenAnswer(invocation -> {
			OrderRefund refund = invocation.getArgument(0);
			refund.setId("refund-1");
			return 1;
		});
		when(orderRefundMapper.selectById("refund-1")).thenAnswer(invocation -> {
			OrderRefund refund = new OrderRefund();
			refund.setId("refund-1");
			refund.setOrderId("order-1");
			refund.setOrderItemId("item-1");
			refund.setRefundAmount(new BigDecimal("19.90"));
			refund.setRefundTradeNo("RT-1");
			refund.setArrivalStatus("1");
			return refund;
		});

		boolean result = refundService.refundWholeOrder("order-1", "拼团失败自动退款");

		assertThat(result).isTrue();
		ArgumentCaptor<OrderRefund> captor = ArgumentCaptor.forClass(OrderRefund.class);
		verify(remoteRefundService).refunds(any());
		verify(orderRefundMapper).insert(captor.capture());
		assertThat(captor.getValue().getRefundReason()).isEqualTo("拼团失败自动退款");
	}

	private static <T> T mock(Class<T> type) {
		return org.mockito.Mockito.mock(type);
	}
}
