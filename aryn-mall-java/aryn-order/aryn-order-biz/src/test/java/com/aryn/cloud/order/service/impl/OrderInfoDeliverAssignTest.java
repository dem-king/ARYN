package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.logistics.util.Kuaidi100Utils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.OrderDeliverAssignDTO;
import com.aryn.cloud.order.api.dto.OrderDeliveryDTO;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderConfig;
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
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IShoppingCartService;
import com.aryn.cloud.order.validator.DeliveryContextValidator;
import com.aryn.cloud.order.validator.PurchaseSceneValidator;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城配送/内部配送订单的发货派单（免物流单号）与快递发货误用拦截。
 */
class OrderInfoDeliverAssignTest {

	private OrderInfoServiceImpl service;

	private OrderInfoMapper orderInfoMapper;

	private IOrderItemService orderItemService;

	private IDeliveryTaskService deliveryTaskService;

	private com.aryn.cloud.order.service.IOrderDeliveryStateService orderDeliveryStateService;

	private OrderDeliveryMapper orderDeliveryMapper;

	private IOrderConfigService orderConfigService;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderItemEntity.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		orderConfigService = mock(IOrderConfigService.class);
		orderItemService = mock(IOrderItemService.class);
		deliveryTaskService = mock(IDeliveryTaskService.class);
		orderDeliveryStateService = mock(com.aryn.cloud.order.service.IOrderDeliveryStateService.class);
		orderDeliveryMapper = mock(OrderDeliveryMapper.class);

		service = new OrderInfoServiceImpl(orderConfigService, mock(Kuaidi100Utils.class),
				mock(OrderItemMapper.class), mock(RemoteGoodsSkuService.class),
				mock(com.aryn.cloud.product.api.remote.RemoteGoodsSpuService.class),
				mock(RemoteMallUserService.class), orderItemService, mock(IShoppingCartService.class),
				mock(com.aryn.cloud.pay.api.remote.RemotePayService.class), mock(ApplicationEventPublisher.class),
				mock(OrderPriceComputeService.class), mock(OrderWxDeliveryService.class),
				mock(OrderStatisticsQueryService.class), mock(OrderAppraiseService.class),
				mock(RemoteUserAddressService.class), orderDeliveryMapper, mock(OrderRefundMapper.class),
				mock(RemoteCouponUserService.class), mock(RemoteSeckillService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteGroupBuyService.class), mock(RocketMQTemplate.class),
				mock(CallbackPrefixProperties.class), deliveryTaskService,
				orderDeliveryStateService,
				mock(com.aryn.cloud.order.service.IDeliveryAreaService.class), mock(PromotionSnapshotMapper.class),
				mock(PurchaseSceneValidator.class), mock(DeliveryContextValidator.class),
				mock(OrderPaySuccessNotifier.class), mock(RemotePromotionEngine.class),
				mock(com.aryn.cloud.upms.api.remote.RemoteMaterialService.class), mock(ObjectProvider.class));

		orderInfoMapper = mock(OrderInfoMapper.class);
		ReflectionTestUtils.setField(service, "baseMapper", orderInfoMapper);
	}

	private OrderInfo order(String deliveryWay, String status) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setDeliveryWay(deliveryWay);
		orderInfo.setStatus(status);
		return orderInfo;
	}

	private OrderDeliverAssignDTO assignDto() {
		OrderDeliverAssignDTO dto = new OrderDeliverAssignDTO();
		dto.setOrderId("order-1");
		dto.setStaffId("staff-1");
		return dto;
	}

	@Test
	void deliverOrderRejectsTaskDrivenDeliveryWay() {
		when(orderConfigService.getConfig()).thenReturn(new OrderConfig());
		when(orderInfoMapper.selectById("order-1")).thenReturn(order(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		OrderDeliveryDTO request = new OrderDeliveryDTO();
		request.setOrderId("order-1");
		request.setLogisticsCompanyCode("SF");
		request.setLogisticsCompanyName("顺丰");
		request.setLogisticsNo("SF123");

		assertThatThrownBy(() -> service.deliverOrder(request))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "商城配送/内部配送订单无需物流单号，请直接派单给司机");
		// 快递发货单不能创建，避免任务与订单双轨不一致
		verify(orderDeliveryMapper, never()).insert(any(com.aryn.cloud.order.api.entity.OrderDelivery.class));
	}

	@Test
	void deliverAndAssignAssignsDriverWithoutLogistics() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		OrderItemEntity item = new OrderItemEntity();
		item.setStatus(OrderItemStatusEnum.PAID.getCode());
		List<OrderItemEntity> items = List.of(item);
		when(orderItemService.list(any(Wrapper.class))).thenReturn(items);
		when(deliveryTaskService.assignByOrderId("order-1", "staff-1")).thenReturn("trip-1");

		assertThat(service.deliverAndAssignOrder(assignDto())).isTrue();

		verify(deliveryTaskService).createTaskOnPay(any(OrderInfo.class), eq(items));
		verify(deliveryTaskService).assignByOrderId("order-1", "staff-1");
		// 派单路径不产生快递发货单
		verify(orderDeliveryMapper, never()).insert(any(com.aryn.cloud.order.api.entity.OrderDelivery.class));
	}

	@Test
	void deliverAndAssignRejectsExpressOrder() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));

		assertThatThrownBy(() -> service.deliverAndAssignOrder(assignDto()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "快递配送订单请填写物流信息发货");
	}

	@Test
	void deliverAndAssignRejectsNonWaitingOrder() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()));

		assertThatThrownBy(() -> service.deliverAndAssignOrder(assignDto()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单不是待发货状态，无法派单");
	}

	@Test
	void deliverAndAssignRejectsAbnormalItemStatus() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		OrderItemEntity item = new OrderItemEntity();
		item.setStatus(OrderItemStatusEnum.SHIPPED.getCode());
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of(item));

		assertThatThrownBy(() -> service.deliverAndAssignOrder(assignDto()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单商品状态已变化，无法派单");
	}

	@Test
	void deliverAndAssignRejectsOrderWithoutItems() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of());

		assertThatThrownBy(() -> service.deliverAndAssignOrder(assignDto()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单商品不存在");
	}

	@Test
	void getOrderByIdFillsDeliveryTaskForTaskDrivenOrder() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		when(orderInfoMapper.selectOrderById("order-1")).thenReturn(orderInfo);
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		when(deliveryTaskService.getTaskByOrderId("order-1")).thenReturn(task);

		OrderInfo result = service.getOrderById("order-1");

		assertThat(result.getDeliveryTask()).isSameAs(task);
	}

	@Test
	void getOrderByIdSkipsDeliveryTaskForExpressOrder() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		when(orderInfoMapper.selectOrderById("order-1")).thenReturn(orderInfo);

		OrderInfo result = service.getOrderById("order-1");

		assertThat(result.getDeliveryTask()).isNull();
		verify(deliveryTaskService, never()).getTaskByOrderId(any());
	}

	@Test
	void adminPageFillsDeliveryTaskForTaskDrivenOrders() {
		OrderInfo taskDriven = order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		OrderInfo express = order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		express.setId("order-2");
		Page<OrderInfo> page = new Page<>(1, 10);
		page.setRecords(List.of(taskDriven, express));
		when(orderInfoMapper.selectAdminPage(any(), any())).thenReturn(page);
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setOrderId("order-1");
		when(deliveryTaskService.mapByOrderIds(List.of("order-1"))).thenReturn(Map.of("order-1", task));

		IPage<OrderInfo> result = service.adminPage(new Page<>(1, 10), new OrderInfo());

		assertThat(result.getRecords().get(0).getDeliveryTask()).isSameAs(task);
		assertThat(result.getRecords().get(1).getDeliveryTask()).isNull();
	}

	@Test
	void adminPageSkipsTaskQueryWithoutTaskDrivenOrders() {
		Page<OrderInfo> page = new Page<>(1, 10);
		page.setRecords(List.of(
				order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode())));
		when(orderInfoMapper.selectAdminPage(any(), any())).thenReturn(page);

		service.adminPage(new Page<>(1, 10), new OrderInfo());

		verify(deliveryTaskService, never()).mapByOrderIds(any());
	}

}
