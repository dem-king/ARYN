package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryAssignRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryReassignRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.upms.api.remote.RemoteDeliveryStaffService;
import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskAdminServiceTest {

	@Test
	void assignmentChecksEligibilityUsesVersionAndAppendsLog() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT, 0, 1));
		DeliveryStaffVO staff = staff();
		when(fixture.remoteStaffService.getEligibleStaff("tenant-1", "staff-1")).thenReturn(staff);
		when(fixture.taskMapper.assign(eq("tenant-1"), eq("task-1"),
			eq(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT.name()), eq(0), eq(staff), eq("admin-1"),
			eq("管理员"), any())).thenReturn(1);
		when(fixture.logMapper.insert(any(OrderDeliveryTaskLog.class))).thenReturn(1);
		DeliveryAssignRequest request = new DeliveryAssignRequest();
		request.setAssigneeId("staff-1");
		request.setVersion(0);
		request.setRequestId("assign-1");

		withOperator(() -> assertThat(fixture.service.assign("task-1", request)).isTrue());

		verify(fixture.remoteStaffService).getEligibleStaff("tenant-1", "staff-1");
		verify(fixture.logMapper).insert(any(OrderDeliveryTaskLog.class));
	}

	@Test
	void firstAssignmentOnlyAllowsWaitingAssignment() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.ASSIGNED, 0, 1));
		when(fixture.remoteStaffService.getEligibleStaff("tenant-1", "staff-1")).thenReturn(staff());
		DeliveryAssignRequest request = new DeliveryAssignRequest();
		request.setAssigneeId("staff-1");
		request.setVersion(0);
		request.setRequestId("assign-1");

		withOperator(() -> assertThatThrownBy(() -> fixture.service.assign("task-1", request))
			.isInstanceOf(ArynBusinessException.class));

		verify(fixture.taskMapper, never()).assign(anyString(), anyString(), anyString(), anyInt(),
			any(), anyString(), anyString(), any());
	}

	@Test
	void reassignRequiresReasonIncrementsAttemptAndResetsPickingItems() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.PICKING, 2, 4));
		DeliveryStaffVO staff = staff();
		when(fixture.remoteStaffService.getEligibleStaff("tenant-1", "staff-1")).thenReturn(staff);
		when(fixture.taskMapper.reassign(eq("tenant-1"), eq("task-1"),
			eq(DeliveryTaskStatusEnum.PICKING.name()), eq(4), eq(staff), eq("admin-1"), eq("管理员"),
			any())).thenReturn(1);
		when(fixture.logMapper.insert(any(OrderDeliveryTaskLog.class))).thenReturn(1);
		DeliveryReassignRequest request = new DeliveryReassignRequest();
		request.setAssigneeId("staff-1");
		request.setVersion(4);
		request.setRequestId("reassign-1");
		request.setReasonCode("STAFF_BUSY");
		request.setDescription("原配送员临时无法继续配送");

		withOperator(() -> assertThat(fixture.service.reassign("task-1", request)).isTrue());

		verify(fixture.taskItemMapper).resetForAttempt("tenant-1", "task-1", 3);
	}

	@Test
	void reassignRejectsMissingReasonAndTerminalStates() {
		for (DeliveryTaskStatusEnum status : new DeliveryTaskStatusEnum[] {
			DeliveryTaskStatusEnum.DELIVERED, DeliveryTaskStatusEnum.RETURN_PENDING, DeliveryTaskStatusEnum.CLOSED }) {
			Fixture fixture = fixture(task(status, 1, 0));
			DeliveryReassignRequest request = new DeliveryReassignRequest();
			request.setAssigneeId("staff-1");
			request.setVersion(0);
			request.setRequestId("reassign-1");
			request.setReasonCode("TERMINAL");
			withOperator(() -> assertThatThrownBy(() -> fixture.service.reassign("task-1", request))
				.isInstanceOf(ArynBusinessException.class));
		}

		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.ASSIGNED, 1, 0));
		DeliveryReassignRequest missingReason = new DeliveryReassignRequest();
		missingReason.setAssigneeId("staff-1");
		missingReason.setVersion(0);
		missingReason.setRequestId("reassign-2");
		withOperator(() -> assertThatThrownBy(() -> fixture.service.reassign("task-1", missingReason))
			.isInstanceOf(ArynBusinessException.class));
		verify(fixture.remoteStaffService, never()).getEligibleStaff(anyString(), anyString());
	}

	@Test
	void staleVersionDoesNotAppendLog() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT, 1, 0));
		DeliveryStaffVO staff = staff();
		when(fixture.remoteStaffService.getEligibleStaff("tenant-1", "staff-1")).thenReturn(staff);
		when(fixture.taskMapper.assign(eq("tenant-1"), eq("task-1"),
			eq(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT.name()), eq(0), eq(staff), eq("admin-1"),
			eq("管理员"), any())).thenReturn(0);
		DeliveryAssignRequest request = new DeliveryAssignRequest();
		request.setAssigneeId("staff-1");
		request.setVersion(0);
		request.setRequestId("assign-stale");

		withOperator(() -> assertThatThrownBy(() -> fixture.service.assign("task-1", request))
			.isInstanceOf(ArynBusinessException.class));

		verify(fixture.logMapper, never()).insert(any(OrderDeliveryTaskLog.class));
	}

	@Test
	void logFailurePropagatesForTransactionRollback() {
		Fixture fixture = fixture(task(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT, 1, 0));
		DeliveryStaffVO staff = staff();
		when(fixture.remoteStaffService.getEligibleStaff("tenant-1", "staff-1")).thenReturn(staff);
		when(fixture.taskMapper.assign(eq("tenant-1"), eq("task-1"),
			eq(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT.name()), eq(0), eq(staff), eq("admin-1"),
			eq("管理员"), any())).thenReturn(1);
		when(fixture.logMapper.insert(any(OrderDeliveryTaskLog.class)))
			.thenThrow(new IllegalStateException("log insert failed"));
		DeliveryAssignRequest request = new DeliveryAssignRequest();
		request.setAssigneeId("staff-1");
		request.setVersion(0);
		request.setRequestId("assign-log-failure");

		withOperator(() -> assertThatThrownBy(() -> fixture.service.assign("task-1", request))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("log insert failed"));
	}

	private Fixture fixture(OrderDeliveryTask task) {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper taskItemMapper = mock(OrderDeliveryTaskItemMapper.class);
		OrderDeliveryTaskLogMapper logMapper = mock(OrderDeliveryTaskLogMapper.class);
		RemoteDeliveryStaffService remoteStaffService = mock(RemoteDeliveryStaffService.class);
		when(taskMapper.selectByTenantAndId("tenant-1", "task-1")).thenReturn(task);
		DeliveryTaskAdminService service = new DeliveryTaskAdminService(taskMapper, taskItemMapper, logMapper,
			new DeliveryTaskTransitionPolicy(), remoteStaffService);
		return new Fixture(service, taskMapper, taskItemMapper, logMapper, remoteStaffService);
	}

	private OrderDeliveryTask task(DeliveryTaskStatusEnum status, int attemptNo, int version) {
		return new OrderDeliveryTask().setId("task-1")
			.setTenantId("tenant-1")
			.setStatus(status.name())
			.setAttemptNo(attemptNo)
			.setVersion(version);
	}

	private DeliveryStaffVO staff() {
		DeliveryStaffVO staff = new DeliveryStaffVO();
		staff.setId("staff-1");
		staff.setNickname("配送员张三");
		staff.setPhone("13800000000");
		return staff;
	}

	private void withOperator(Runnable action) {
		ArynUser user = new ArynUser();
		user.setUserId("admin-1");
		user.setNickname("管理员");
		user.setTenantId("tenant-1");
		try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
			security.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOB)).thenReturn(user);
			action.run();
		}
	}

	private record Fixture(DeliveryTaskAdminService service, OrderDeliveryTaskMapper taskMapper,
			OrderDeliveryTaskItemMapper taskItemMapper, OrderDeliveryTaskLogMapper logMapper,
			RemoteDeliveryStaffService remoteStaffService) {
	}

}
