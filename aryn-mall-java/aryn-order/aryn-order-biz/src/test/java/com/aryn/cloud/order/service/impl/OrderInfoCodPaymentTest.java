package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.PayConfirmDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.mapper.PromotionSnapshotMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.order.validator.DeliveryContextValidator;
import com.aryn.cloud.order.validator.PurchaseSceneValidator;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 货到付款（paymentType=3）下单/确认收款/取消的单元测试。
 */
class OrderInfoCodPaymentTest {

	private OrderInfoServiceImpl service;

	private OrderInfoMapper orderInfoMapper;

	private OrderItemMapper orderItemMapper;

	private IOrderItemService orderItemService;

	private RemoteMallUserService remoteMallUserService;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private RemoteUserAddressService remoteUserAddressService;

	private com.aryn.cloud.order.service.IDeliveryAreaService deliveryAreaService;

	private com.aryn.cloud.order.service.IDeliveryTaskService deliveryTaskService;

	private OrderPaySuccessNotifier orderPaySuccessNotifier;

	private RemotePromotionEngine promotionEngine;

	private com.aryn.cloud.upms.api.remote.RemoteMaterialService remoteMaterialService;

	private OrderPriceComputeService orderPriceComputeService;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItemEntity.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		IOrderConfigService orderConfigService = mock(IOrderConfigService.class);
		Kuaidi100Utils kuaidi100Utils = mock(Kuaidi100Utils.class);
		orderItemMapper = mock(OrderItemMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		orderItemService = mock(IOrderItemService.class);
		IShoppingCartService shoppingCartService = mock(IShoppingCartService.class);
		ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
		orderPriceComputeService = mock(OrderPriceComputeService.class);
		OrderWxDeliveryService orderWxDeliveryService = mock(OrderWxDeliveryService.class);
		OrderStatisticsQueryService orderStatisticsQueryService = mock(OrderStatisticsQueryService.class);
		OrderAppraiseService orderAppraiseService = mock(OrderAppraiseService.class);
		remoteUserAddressService = mock(RemoteUserAddressService.class);
		OrderDeliveryMapper orderDeliveryMapper = mock(OrderDeliveryMapper.class);
		OrderRefundMapper orderRefundMapper = mock(OrderRefundMapper.class);
		RemoteCouponUserService remoteCouponUserService = mock(RemoteCouponUserService.class);
		RemoteSeckillService remoteSeckillService = mock(RemoteSeckillService.class);
		RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
		com.aryn.cloud.common.core.entity.CallbackPrefixProperties callbackPrefixProperties = mock(
				com.aryn.cloud.common.core.entity.CallbackPrefixProperties.class);
		deliveryTaskService = mock(com.aryn.cloud.order.service.IDeliveryTaskService.class);
		deliveryAreaService = mock(com.aryn.cloud.order.service.IDeliveryAreaService.class);
		PromotionSnapshotMapper promotionSnapshotMapper = mock(PromotionSnapshotMapper.class);
		PurchaseSceneValidator purchaseSceneValidator = mock(PurchaseSceneValidator.class);
		DeliveryContextValidator deliveryContextValidator = mock(DeliveryContextValidator.class);
		orderPaySuccessNotifier = mock(OrderPaySuccessNotifier.class);
		promotionEngine = mock(RemotePromotionEngine.class);
		remoteMaterialService = mock(com.aryn.cloud.upms.api.remote.RemoteMaterialService.class);
		ObjectProvider<ISharedCartService> sharedCartServiceProvider = mock(ObjectProvider.class);

		service = new OrderInfoServiceImpl(orderConfigService, kuaidi100Utils, orderItemMapper, remoteGoodsSkuService,
				mock(com.aryn.cloud.product.api.remote.RemoteGoodsSpuService.class),
				remoteMallUserService, orderItemService, shoppingCartService,
				mock(com.aryn.cloud.pay.api.remote.RemotePayService.class), applicationEventPublisher,
				orderPriceComputeService, orderWxDeliveryService, orderStatisticsQueryService, orderAppraiseService,
				remoteUserAddressService, orderDeliveryMapper, orderRefundMapper, remoteCouponUserService,
				remoteSeckillService, rocketMQTemplate, callbackPrefixProperties, deliveryTaskService,
				deliveryAreaService, promotionSnapshotMapper, purchaseSceneValidator, deliveryContextValidator,
				orderPaySuccessNotifier, promotionEngine, remoteMaterialService, sharedCartServiceProvider);

		orderInfoMapper = mock(OrderInfoMapper.class);
		ReflectionTestUtils.setField(service, "baseMapper", orderInfoMapper);
	}

	private CreateOrderDTO codOrderDto() {
		CreateOrderSkuReqDTO skuReq = new CreateOrderSkuReqDTO();
		skuReq.setSkuId("sku-1");
		skuReq.setQuantity(1);
		CreateOrderDTO dto = new CreateOrderDTO();
		dto.setUserId("u1");
		dto.setRequestId("req-1");
		dto.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_3);
		dto.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		dto.setCreateWay("2");
		dto.setUserAddressId("addr-1");
		dto.setSkuReqList(List.of(skuReq));
		return dto;
	}

	@Test
	void createOrderWithCodSkipsWaitingForPaymentAndCreatesDeliveryTask() {
		when(orderInfoMapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(orderInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);
		when(remoteMallUserService.getUserById("u1")).thenReturn(new UserInfoVO());
		when(remoteGoodsSkuService.getBySkuIds(any())).thenReturn(List.of(new GoodsSku()));
		OrderItemEntity item = new OrderItemEntity();
		when(orderPriceComputeService.generateOrderItems(any(), any())).thenReturn(List.of(item));
		when(remoteMallUserService.getMemberBenefits("u1")).thenReturn(null);
		UserAddress address = new UserAddress();
		address.setRecipientName("张三");
		address.setTelephone("13800000000");
		when(remoteUserAddressService.getById("addr-1", "u1")).thenReturn(address);
		when(deliveryAreaService.isAddressInDeliveryArea(any(), any(), any())).thenReturn(true);
		when(orderItemService.saveBatch(any())).thenReturn(true);

		OrderInfo order = service.createOrder(codOrderDto());

		assertThat(order.getStatus()).isEqualTo(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		assertThat(order.getPayStatus()).isEqualTo(CommonConstants.NO);
		assertThat(order.getPaymentType()).isEqualTo(MallOrderConstants.PAYMENT_TYPE_3);
		assertThat(item.getStatus()).isEqualTo(OrderItemStatusEnum.PAID.getCode());
		assertThat(item.getOrderId()).isEqualTo(order.getId());
		verify(deliveryTaskService).createTaskOnPay(order, List.of(item));
	}

	@Test
	void createOrderRejectsOnlinePaymentTypeDeclaredByClient() {
		CreateOrderDTO dto = codOrderDto();
		dto.setPaymentType("1");

		assertThatThrownBy(() -> service.createOrder(dto))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "支付类型不合法");
		verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
	}

	@Test
	void createOrderRejectsCodWithUnsupportedDeliveryWay() {
		CreateOrderDTO dto = codOrderDto();
		dto.setDeliveryWay("1");

		assertThatThrownBy(() -> service.createOrder(dto))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "该配送方式不支持货到付款");
		verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
	}

	@Test
	void confirmOfflinePaymentMarksPaidAndNotifies() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setPayStatus(CommonConstants.NO);
		order.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		order.setTenantId("t1");
		order.setPaymentPrice(new java.math.BigDecimal("1803"));
		when(orderInfoMapper.selectById("o1")).thenReturn(order);
		when(orderInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		OrderItemEntity item = new OrderItemEntity();
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of(item));

		assertThat(service.confirmOfflinePayment("o1", payConfirmDto("1800"))).isTrue();

		assertThat(order.getPayStatus()).isEqualTo(CommonConstants.YES);
		assertThat(order.getPaymentTime()).isNotNull();
		assertThat(order.getActualPayPrice()).isEqualByComparingTo("1800");
		verify(promotionEngine).confirm("t1", "o1");
		verify(orderPaySuccessNotifier).notify(order, List.of(item));
	}

	private PayConfirmDTO payConfirmDto(String actualPayPrice) {
		PayConfirmDTO dto = new PayConfirmDTO();
		dto.setActualPayPrice(new java.math.BigDecimal(actualPayPrice));
		return dto;
	}

	@Test
	void confirmOfflinePaymentRejectsMissingActualPayPrice() {
		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", new PayConfirmDTO()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "请填写实收金额");
		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", null))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "请填写实收金额");
	}

	@Test
	void confirmOfflinePaymentRejectsActualPayPriceOverReceivable() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setPayStatus(CommonConstants.NO);
		order.setPaymentPrice(new java.math.BigDecimal("1803"));
		when(orderInfoMapper.selectById("o1")).thenReturn(order);

		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", payConfirmDto("1803.01")))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("实收金额不能大于应收金额"));
	}

	@Test
	void confirmOfflinePaymentSnapshotsPayVoucherUrls() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setPayStatus(CommonConstants.NO);
		order.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		order.setTenantId("t1");
		order.setPaymentPrice(new java.math.BigDecimal("1803"));
		when(orderInfoMapper.selectById("o1")).thenReturn(order);
		when(orderInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of());
		when(remoteMaterialService.mapUrlByIds(List.of("m1", "m2")))
			.thenReturn(java.util.Map.of("m1", "http://f/1.png", "m2", "http://f/2.png"));

		PayConfirmDTO dto = payConfirmDto("1800");
		dto.setVoucherMaterialIds(List.of("m1", "m2", "m1"));

		assertThat(service.confirmOfflinePayment("o1", dto)).isTrue();

		// 走的是 lambdaUpdate().set(...) 而非 updateById（not_null 策略会静默丢字段）
		org.mockito.ArgumentCaptor<Wrapper<OrderInfo>> captor = org.mockito.ArgumentCaptor
			.forClass(Wrapper.class);
		verify(orderInfoMapper).update(isNull(), captor.capture());
		String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderInfo>) captor
			.getValue()).getSqlSet();
		assertThat(sqlSet).contains("actual_pay_price").contains("pay_vouchers");
		assertThat(order.getPayVouchers()).contains("m1").contains("http://f/1.png");
	}

	@Test
	void confirmOfflinePaymentRejectsUnknownVoucherMaterial() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setPayStatus(CommonConstants.NO);
		order.setPaymentPrice(new java.math.BigDecimal("1803"));
		when(orderInfoMapper.selectById("o1")).thenReturn(order);
		when(remoteMaterialService.mapUrlByIds(List.of("gone")))
			.thenReturn(java.util.Collections.emptyMap());

		PayConfirmDTO dto = payConfirmDto("1800");
		dto.setVoucherMaterialIds(List.of("gone"));

		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", dto))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "付款凭证素材不存在或已删除，请重新上传");
		verify(orderInfoMapper, never()).update(isNull(), any(Wrapper.class));
	}

	@Test
	void confirmOfflinePaymentRejectsNonCodOrder() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType("2");
		when(orderInfoMapper.selectById("o1")).thenReturn(order);

		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", payConfirmDto("100")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "仅货到付款订单支持确认收款");
	}

	@Test
	void confirmOfflinePaymentIsIdempotentGuardedOnPayStatus() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setPayStatus(CommonConstants.NO);
		when(orderInfoMapper.selectById("o1")).thenReturn(order);
		// 条件更新未命中：说明 payStatus 已被并发置 1 或订单已取消
		when(orderInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

		assertThatThrownBy(() -> service.confirmOfflinePayment("o1", payConfirmDto("100")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单状态已变化，无法确认收款");
		verify(orderPaySuccessNotifier, never()).notify(any(), any());
	}

	@Test
	void cancelCodOrderClosesWaitingAssignTaskBeforeCancellingOrder() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		order.setPayStatus(CommonConstants.NO);
		when(deliveryTaskService.cancelWaitingAssignByOrderId("o1")).thenReturn(Boolean.TRUE);
		when(orderInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		assertThat(service.cancelOrder(order)).isEqualTo("o1");

		InOrder inOrder = inOrder(deliveryTaskService, orderInfoMapper);
		inOrder.verify(deliveryTaskService).cancelWaitingAssignByOrderId("o1");
		inOrder.verify(orderInfoMapper).update(isNull(), any(Wrapper.class));
	}

	@Test
	void cancelCodOrderRejectedWhenDeliveryTaskAssigned() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setPaymentType(MallOrderConstants.PAYMENT_TYPE_3);
		order.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		order.setPayStatus(CommonConstants.NO);
		doThrow(new ArynBusinessException("配送已安排，无法取消"))
				.when(deliveryTaskService).cancelWaitingAssignByOrderId("o1");

		assertThatThrownBy(() -> service.cancelOrder(order))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "配送已安排，无法取消");
		verify(orderInfoMapper, never()).update(any(), any());
	}

	@Test
	void cancelOnlineOrderKeepsOriginalGuardWithoutTaskInteraction() {
		OrderInfo order = new OrderInfo();
		order.setId("o1");
		order.setStatus(OrderStatusEnum.WAITING_FOR_PAYMENT.getCode());
		order.setPayStatus(CommonConstants.NO);
		when(orderInfoMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		when(orderItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		assertThat(service.cancelOrder(order)).isEqualTo("o1");

		verify(deliveryTaskService, never()).cancelWaitingAssignByOrderId(anyString());
		verify(orderInfoMapper).update(isNull(), any(Wrapper.class));
	}

}
