package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryCompleteRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryExceptionRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryPickupRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskActionEnum;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskStaffServiceTest {

	@Test
	void reassignedStaffCannotReadOrOperateFormerTask() {
		Fixture fixture = fixture(null);

		withStaff(() -> {
			assertThatThrownBy(() -> fixture.service.get("task-1")).isInstanceOf(ArynBusinessException.class);
			DeliveryPickupRequest request = pickupRequest("pickup-1");
			assertThatThrownBy(() -> fixture.service.pickup("task-1", request))
				.isInstanceOf(ArynBusinessException.class);
		});

		verify(fixture.taskMapper, org.mockito.Mockito.times(2))
			.selectByTenantAssigneeAndId("tenant-1", "staff-1", "task-1");
	}

	@Test
	void pickupRequiresAllCurrentAttemptItemsChecked() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.PICKING));
		when(fixture.taskItemMapper.countUnchecked("tenant-1", "task-1", 2)).thenReturn(1);

		withStaff(() -> assertThatThrownBy(() -> fixture.service.pickup("task-1", pickupRequest("pickup-1")))
			.isInstanceOf(ArynBusinessException.class));

		verify(fixture.taskMapper, never()).updateStaffStatus(anyString(), anyString(), anyString(), anyString(),
			anyString(), any(Integer.class), any(), anyString());
	}

	@Test
	void pickupUpdatesTaskOrderAndItemsInOneServiceTransaction() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.PICKING));
		when(fixture.taskItemMapper.countUnchecked("tenant-1", "task-1", 2)).thenReturn(0);
		when(fixture.taskMapper.updateStaffStatus(anyString(), anyString(), anyString(), anyString(), anyString(),
			any(Integer.class), any(), anyString())).thenReturn(1);
		when(fixture.orderInfoMapper.markMallDeliveryPickedUp(anyString(), anyString(), any(), anyString()))
			.thenReturn(1);
		when(fixture.orderItemMapper.markMallDeliveryShipped("tenant-1", "order-1")).thenReturn(2);
		when(fixture.logMapper.insert(any(OrderDeliveryTaskLog.class))).thenReturn(1);

		withStaff(() -> assertThat(fixture.service.pickup("task-1", pickupRequest("pickup-1"))).isTrue());

		verify(fixture.orderInfoMapper).markMallDeliveryPickedUp(anyString(), anyString(), any(), anyString());
		verify(fixture.orderItemMapper).markMallDeliveryShipped("tenant-1", "order-1");
	}

	@Test
	void deliveredRequiresOneToSixMaterialIds() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.DELIVERING));
		DeliveryCompleteRequest empty = completeRequest("delivered-empty", List.of());
		DeliveryCompleteRequest tooMany = completeRequest("delivered-many",
			List.of("1", "2", "3", "4", "5", "6", "7"));

		withStaff(() -> {
			assertThatThrownBy(() -> fixture.service.complete("task-1", empty))
				.isInstanceOf(ArynBusinessException.class);
			assertThatThrownBy(() -> fixture.service.complete("task-1", tooMany))
				.isInstanceOf(ArynBusinessException.class);
		});
	}

	@Test
	void exceptionRequiresDescriptionButAllowsNoImages() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.DELIVERING));
		DeliveryExceptionRequest request = new DeliveryExceptionRequest();
		request.setVersion(1);
		request.setRequestId("exception-1");
		request.setReasonCode("CUSTOMER_ABSENT");

		withStaff(() -> assertThatThrownBy(() -> fixture.service.reportException("task-1", request))
			.isInstanceOf(ArynBusinessException.class));
	}

	@Test
	void duplicateRequestReturnsOriginalResultWithoutMoreWrites() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.DELIVERED));
		when(fixture.logMapper.selectByRequest("tenant-1", "task-1", DeliveryTaskActionEnum.DELIVER.name(),
			"delivered-1")).thenReturn(new OrderDeliveryTaskLog().setId("log-1"));
		DeliveryCompleteRequest request = completeRequest("delivered-1", List.of("material-1"));

		withStaff(() -> assertThat(fixture.service.complete("task-1", request)).isTrue());

		verify(fixture.taskMapper, never()).updateStaffStatus(anyString(), anyString(), anyString(), anyString(),
			anyString(), any(Integer.class), any(), anyString());
		verify(fixture.evidenceMapper, never()).insert(any(OrderDeliveryEvidence.class));
	}

	private Fixture fixture(OrderDeliveryTask task) {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper taskItemMapper = mock(OrderDeliveryTaskItemMapper.class);
		OrderDeliveryEvidenceMapper evidenceMapper = mock(OrderDeliveryEvidenceMapper.class);
		OrderDeliveryTaskLogMapper logMapper = mock(OrderDeliveryTaskLogMapper.class);
		OrderInfoMapper orderInfoMapper = mock(OrderInfoMapper.class);
		OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
		when(taskMapper.selectByTenantAssigneeAndId("tenant-1", "staff-1", "task-1")).thenReturn(task);
		DeliveryTaskStaffService service = new DeliveryTaskStaffService(taskMapper, taskItemMapper, evidenceMapper,
			logMapper, orderInfoMapper, orderItemMapper, new DeliveryTaskTransitionPolicy());
		return new Fixture(service, taskMapper, taskItemMapper, evidenceMapper, logMapper,
			orderInfoMapper, orderItemMapper);
	}

	private OrderDeliveryTask task(DeliveryTaskStatusEnum status) {
		return new OrderDeliveryTask().setId("task-1")
			.setOrderId("order-1")
			.setTenantId("tenant-1")
			.setAssigneeId("staff-1")
			.setStatus(status.name())
			.setAttemptNo(2)
			.setVersion(1);
	}

	private DeliveryPickupRequest pickupRequest(String requestId) {
		DeliveryPickupRequest request = new DeliveryPickupRequest();
		request.setVersion(1);
		request.setRequestId(requestId);
		return request;
	}

	private DeliveryCompleteRequest completeRequest(String requestId, List<String> materialIds) {
		DeliveryCompleteRequest request = new DeliveryCompleteRequest();
		request.setVersion(1);
		request.setRequestId(requestId);
		request.setMaterialIds(materialIds);
		return request;
	}

	private void withStaff(Runnable action) {
		ArynUser user = new ArynUser();
		user.setUserId("staff-1");
		user.setNickname("配送员张三");
		user.setTenantId("tenant-1");
		try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
			security.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOB)).thenReturn(user);
			action.run();
		}
	}

	private record Fixture(DeliveryTaskStaffService service, OrderDeliveryTaskMapper taskMapper,
			OrderDeliveryTaskItemMapper taskItemMapper, OrderDeliveryEvidenceMapper evidenceMapper,
			OrderDeliveryTaskLogMapper logMapper, OrderInfoMapper orderInfoMapper, OrderItemMapper orderItemMapper) {
	}

}
