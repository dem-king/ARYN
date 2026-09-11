package com.aryn.cloud.order.event;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.event.listener.ArynOrderPayEventListener;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.impl.DeliveryTaskServiceImpl;
import com.aryn.cloud.order.service.IDeliveryEvidenceService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskLogService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 支付回调与配送任务幂等契约测试。
 */
class ArynOrderPayDeliveryIdempotencyTest {

	private static final String TENANT = "tenant-1";

	private DeliveryTaskServiceImpl deliveryTaskService;

	private IOrderInfoService orderInfoService;

	private IOrderItemService orderItemService;

	private IDeliveryTaskService deliveryTaskServiceMock;

	private ArynOrderPayEventListener listener;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
		deliveryTaskService = spy(new DeliveryTaskServiceImpl(mock(IDeliveryTripService.class),
				mock(IDeliveryTaskItemService.class), mock(IDeliveryStaffService.class),
				mock(IDeliveryWarehouseConfigService.class), mock(IDeliveryTaskLogService.class),
				mock(IDeliveryEvidenceService.class), mock(RocketMQTemplate.class)));
		orderInfoService = mock(IOrderInfoService.class);
		orderItemService = mock(IOrderItemService.class);
		deliveryTaskServiceMock = mock(IDeliveryTaskService.class);
		listener = new ArynOrderPayEventListener(orderInfoService, orderItemService,
				mock(com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier.class), deliveryTaskServiceMock,
				mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class));
	}

	private OrderInfo order(String deliveryWay) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setOrderNo("ON20260912001");
		orderInfo.setTenantId(TENANT);
		orderInfo.setDeliveryWay(deliveryWay);
		orderInfo.setPurchaseScene("1");
		orderInfo.setPayStatus(CommonConstants.NO);
		orderInfo.setStatus(OrderStatusEnum.WAITING_FOR_PAYMENT.getCode());
		orderInfo.setVesselId("vessel-1");
		orderInfo.setVesselName("测试轮");
		orderInfo.setVesselCallId("call-1");
		orderInfo.setPortCode("CNSHA");
		orderInfo.setPortName("上海港");
		orderInfo.setBerth("3号泊位");
		orderInfo.setDeliveryWindowStart(LocalDateTime.now().plusDays(1));
		orderInfo.setDeliveryWindowEnd(LocalDateTime.now().plusDays(1).plusHours(6));
		return orderInfo;
	}

	private List<OrderItemEntity> items() {
		OrderItemEntity item = new OrderItemEntity();
		item.setId("item-1");
		item.setOrderId("order-1");
		return List.of(item);
	}

	@Test
	@DisplayName("内部配送支付后创建任务并保存船舶/港口/时间窗快照")
	void way4CreatesTaskWithSnapshot() {
		doReturn(null).when(deliveryTaskService).getOne(any(Wrapper.class));
		doReturn(true).when(deliveryTaskService).save(any(DeliveryTask.class));

		boolean created = deliveryTaskService.createTaskOnPay(order(MallOrderConstants.DELIVERY_WAY_4), items());
		assertTrue(created);

		ArgumentCaptor<DeliveryTask> captor = ArgumentCaptor.forClass(DeliveryTask.class);
		verify(deliveryTaskService).save(captor.capture());
		DeliveryTask task = captor.getValue();
		assertEquals("vessel-1", task.getVesselId());
		assertEquals("测试轮", task.getVesselName());
		assertEquals("call-1", task.getVesselCallId());
		assertEquals("CNSHA", task.getPortCode());
		assertEquals("上海港", task.getPortName());
		assertEquals("3号泊位", task.getBerth());
		assertEquals("1", task.getPurchaseScene());
		assertEquals(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(), task.getStatus());
	}

	@Test
	@DisplayName("同一订单重复创建只保留一个有效任务")
	void duplicateCreationIsIdempotent() {
		DeliveryTask existing = new DeliveryTask();
		existing.setId("task-1");
		doReturn(existing).when(deliveryTaskService).getOne(any(Wrapper.class));

		boolean created = deliveryTaskService.createTaskOnPay(order(MallOrderConstants.DELIVERY_WAY_3), items());
		assertTrue(created);
		verify(deliveryTaskService, never()).save(any(DeliveryTask.class));
	}

	@Test
	@DisplayName("并发创建命中唯一约束时幂等返回已存在任务")
	void concurrentInsertFallsBackToExisting() {
		DeliveryTask existing = new DeliveryTask();
		existing.setId("task-concurrent");
		doReturn(null, existing).when(deliveryTaskService).getOne(any(Wrapper.class));
		doThrow(new DuplicateKeyException("uk_delivery_task_order")).when(deliveryTaskService)
			.save(any(DeliveryTask.class));

		boolean created = deliveryTaskService.createTaskOnPay(order(MallOrderConstants.DELIVERY_WAY_4), items());
		assertTrue(created);
	}

	@Test
	@DisplayName("普通快递订单不自动创建配送任务")
	void expressOrderSkipsTaskCreation() {
		assertFalse(deliveryTaskService.createTaskOnPay(order(MallOrderConstants.DELIVERY_WAY_1), items()));
		verify(deliveryTaskService, never()).save(any(DeliveryTask.class));
	}

	@Test
	@DisplayName("已取消订单的支付回调被忽略，不补建任务")
	void canceledOrderPayCallbackIgnored() {
		OrderInfo canceled = order(MallOrderConstants.DELIVERY_WAY_4);
		canceled.setStatus(OrderStatusEnum.CANCELED.getCode());
		listener.hxPayEventListener(new ArynOrderPayEvent(this, canceled, items()));

		verifyNoInteractions(deliveryTaskServiceMock);
		verify(orderInfoService, never()).update(any(Wrapper.class));
	}

	@Test
	@DisplayName("支付成功事件触发内部配送任务创建")
	void payEventTriggersWay4TaskCreation() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_4);
		doReturn(true).when(orderInfoService).update(any(Wrapper.class));
		doReturn(true).when(orderItemService).updateBatchById(any());

		listener.hxPayEventListener(new ArynOrderPayEvent(this, orderInfo, items()));

		verify(deliveryTaskServiceMock, times(1)).createTaskOnPay(eq(orderInfo), anyList());
		assertEquals(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode(), orderInfo.getStatus());
	}

}
