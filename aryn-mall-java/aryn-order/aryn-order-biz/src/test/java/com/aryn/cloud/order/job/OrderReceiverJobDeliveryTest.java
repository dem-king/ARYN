package com.aryn.cloud.order.job;

import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReceiverJobDeliveryTest {

	@Test
	void autoReceiptQueriesExpressAndDeliveredMallOrdersSeparately() throws Exception {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		RemoteTenantService tenantService = mock(RemoteTenantService.class);
		IOrderConfigService configService = mock(IOrderConfigService.class);
		OrderInfoMapper orderInfoMapper = mock(OrderInfoMapper.class);
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		SysTenant tenant = new SysTenant();
		tenant.setId("tenant-1");
		OrderConfig config = new OrderConfig();
		config.setOrderAutoConfirmDays(3);
		OrderInfo express = new OrderInfo().setId("express-order");
		OrderInfo mall = new OrderInfo().setId("mall-order");
		when(tenantService.list()).thenReturn(List.of(tenant));
		when(configService.getConfig()).thenReturn(config);
		when(orderInfoMapper.selectExpressAutoConfirmOrders(eq("tenant-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(express));
		when(taskMapper.selectMallDeliveryAutoConfirmOrders(eq("tenant-1"), any(LocalDateTime.class)))
			.thenReturn(List.of(mall));
		OrderJobHandler handler = new OrderJobHandler(orderService, tenantService, configService,
			orderInfoMapper, taskMapper);

		handler.orderReceiverJobHandler();

		verify(orderService).receiveOrder(express);
		verify(orderService).receiveOrder(mall);
	}

	@Test
	void autoReceiptSqlUsesDifferentTimeoutClocksAndExcludesDeliveringTasks() throws Exception {
		String orderMapper = Files.readString(Path.of(
			"src/main/java/com/aryn/cloud/order/mapper/OrderInfoMapper.java"));
		String deliveryMapper = Files.readString(Path.of(
			"src/main/resources/mapper/OrderDeliveryTaskMapper.xml"));
		String orderService = Files.readString(Path.of(
			"src/main/java/com/aryn/cloud/order/service/impl/OrderInfoServiceImpl.java"));

		org.assertj.core.api.Assertions.assertThat(orderMapper)
			.contains("delivery_way = '1'")
			.contains("deliver_time < #{deadline}");
		org.assertj.core.api.Assertions.assertThat(deliveryMapper)
			.contains("task.status = 'DELIVERED'")
			.contains("task.delivered_at &lt; #{deadline}");
		org.assertj.core.api.Assertions.assertThat(orderService)
			.contains("deliveryRefundBoundaryService.requireDeliveredForReceipt(orderInfo)");
	}

}
