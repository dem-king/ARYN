
package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.DeliveryOnboardDTO;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.vo.DeliveryStaffOnboardVO;
import com.aryn.cloud.order.mapper.DeliveryAccountBindingMapper;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.security.DeliveryAccessGuard;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryQualificationOperationService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送员服务测试：向导创建走 outbox 授权、删除联动回收角色、解绑幂等
 */
class DeliveryStaffServiceImplTest {

	private IDeliveryAccountBindingService deliveryAccountBindingService;

	private IDeliveryQualificationOperationService qualificationOperationService;

	private DeliveryAccessGuard deliveryAccessGuard;

	private RemoteSysUserService remoteSysUserService;

	private RemoteMallUserService remoteMallUserService;

	private DeliveryStaffServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		deliveryAccountBindingService = mock(IDeliveryAccountBindingService.class);
		qualificationOperationService = mock(IDeliveryQualificationOperationService.class);
		remoteSysUserService = mock(RemoteSysUserService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		deliveryAccessGuard = mock(DeliveryAccessGuard.class);
		service = spy(new DeliveryStaffServiceImpl(deliveryAccountBindingService, qualificationOperationService,
				mock(DeliveryAccountBindingMapper.class), mock(DeliveryTaskMapper.class), deliveryAccessGuard,
				remoteSysUserService, remoteMallUserService));
	}

	private void givenStaff(String id, String userId) {
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId(id);
		staff.setUserId(userId);
		staff.setStaffName("张三");
		doReturn(staff).when(service).getById(id);
		return;
	}

	@Test
	void onboardGrantsQualificationThroughOutbox() {
		givenNormalSysUser();
		doReturn(null).when(service).getByUserId("sys-user-1");
		doAnswer(invocation -> {
			DeliveryStaff saved = invocation.getArgument(0);
			saved.setId("staff-1");
			return true;
		}).when(service).save(any(DeliveryStaff.class));
		DeliveryQualificationOperation operation = new DeliveryQualificationOperation();
		operation.setId("op-1");
		operation.setOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		when(qualificationOperationService.createPending(anyString(), eq("sys-user-1"),
				eq(DeliveryStaffServiceImpl.DELIVERY_ROLE_CODE),
				eq(DeliveryQualificationOperation.OPERATION_GRANT), anyString())).thenReturn(operation);
		when(qualificationOperationService.processPending("op-1")).thenReturn(true);

		DeliveryStaffOnboardVO vo = service.onboard(onboardDto("sys-user-1", true), "admin");

		// 角色授予不再在本地事务内同步 Dubbo 调用，而是事务提交后经 outbox 执行
		verify(qualificationOperationService).createPending(anyString(), eq("sys-user-1"),
				eq(DeliveryStaffServiceImpl.DELIVERY_ROLE_CODE),
				eq(DeliveryQualificationOperation.OPERATION_GRANT), eq("admin"));
		verify(qualificationOperationService).processPending("op-1");
		verify(remoteSysUserService, never()).changeRoleByCode(anyString(), anyString(), eq(true));
		verify(deliveryAccessGuard).invalidateQualificationCache("sys-user-1");
		assertThat(vo.getQualificationStatus()).isEqualTo("GRANTED");
		assertThat(vo.getQualificationMessage()).isNull();
	}

	@Test
	void onboardReportsProcessingWhenGrantPending() {
		givenNormalSysUser();
		doReturn(null).when(service).getByUserId("sys-user-1");
		doAnswer(invocation -> {
			DeliveryStaff saved = invocation.getArgument(0);
			saved.setId("staff-1");
			return true;
		}).when(service).save(any(DeliveryStaff.class));
		DeliveryQualificationOperation operation = new DeliveryQualificationOperation();
		operation.setId("op-2");
		operation.setOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		when(qualificationOperationService.createPending(anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn(operation);
		when(qualificationOperationService.processPending("op-2")).thenReturn(false);

		DeliveryStaffOnboardVO vo = service.onboard(onboardDto("sys-user-1", true), "admin");

		// 授权失败不能伪造已开通：返回处理中，由补偿任务继续重试
		assertThat(vo.getQualificationStatus()).isEqualTo("PROCESSING");
		assertThat(vo.getQualificationMessage()).isNotBlank();
	}

	@Test
	void onboardWithoutQualificationDoesNotCreateOperation() {
		givenNormalSysUser();
		doReturn(null).when(service).getByUserId("sys-user-1");
		doAnswer(invocation -> {
			DeliveryStaff saved = invocation.getArgument(0);
			saved.setId("staff-1");
			return true;
		}).when(service).save(any(DeliveryStaff.class));

		DeliveryStaffOnboardVO vo = service.onboard(onboardDto("sys-user-1", false), "admin");

		assertThat(vo.getQualificationStatus()).isEqualTo("NOT_GRANTED");
		verify(qualificationOperationService, never()).createPending(anyString(), anyString(), anyString(), anyString(),
				anyString());
	}

	@Test
	void deleteStaffUnbindsRevokesSessionsAndQueuesRoleRevoke() {
		givenStaff("staff-1", "sys-user-1");
		doReturn(true).when(service).removeById("staff-1");
		DeliveryAccountBinding binding = new DeliveryAccountBinding();
		binding.setId("binding-1");
		binding.setStatus(DeliveryAccountBinding.STATUS_BOUND);
		binding.setMallUserId("mall-user-1");
		when(deliveryAccountBindingService.getActiveByStaffId("staff-1")).thenReturn(binding);
		DeliveryQualificationOperation operation = new DeliveryQualificationOperation();
		operation.setId("op-3");
		operation.setOperation(DeliveryQualificationOperation.OPERATION_REVOKE);
		when(qualificationOperationService.createPending(eq("staff-1"), eq("sys-user-1"),
				eq(DeliveryStaffServiceImpl.DELIVERY_ROLE_CODE),
				eq(DeliveryQualificationOperation.OPERATION_REVOKE), eq("admin"))).thenReturn(operation);
		when(qualificationOperationService.processPending("op-3")).thenReturn(true);

		boolean ok = service.deleteStaff("staff-1", "admin");

		assertThat(ok).isTrue();
		// 解绑保留历史行
		assertThat(binding.getStatus()).isEqualTo(DeliveryAccountBinding.STATUS_UNBOUND);
		assertThat(binding.getUnbindBy()).isEqualTo("admin");
		verify(deliveryAccountBindingService).updateById(binding);
		// 立即撤销配送会话并回收 delivery_staff 角色
		verify(deliveryAccessGuard).revokeDeliverySessions("sys-user-1");
		verify(qualificationOperationService).processPending("op-3");
	}

	@Test
	void deleteStaffStillSucceedsWhenRoleRevokePending() {
		// 角色回收转入重试不能阻塞删除本身：资料已删，守卫按 staff 不存在拒绝旧 token
		givenStaff("staff-1", "sys-user-1");
		doReturn(true).when(service).removeById("staff-1");
		when(deliveryAccountBindingService.getActiveByStaffId("staff-1")).thenReturn(null);
		DeliveryQualificationOperation operation = new DeliveryQualificationOperation();
		operation.setId("op-4");
		when(qualificationOperationService.createPending(anyString(), anyString(), anyString(), anyString(),
				anyString())).thenReturn(operation);
		when(qualificationOperationService.processPending("op-4")).thenReturn(false);

		assertThat(service.deleteStaff("staff-1", "admin")).isTrue();
		verify(qualificationOperationService).processPending("op-4");
	}

	@Test
	void deleteStaffFailsForUnknownStaff() {
		doReturn(null).when(service).getById("missing");
		assertThatThrownBy(() -> service.deleteStaff("missing", "admin"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "配送员不存在");
	}

	@Test
	void unbindMallUserMarksUnboundAndRevokesSessions() {
		givenStaff("staff-1", "sys-user-1");
		DeliveryAccountBinding binding = new DeliveryAccountBinding();
		binding.setId("binding-1");
		binding.setStatus(DeliveryAccountBinding.STATUS_BOUND);
		binding.setSysUserId("sys-user-1");
		binding.setMallUserId("mall-user-1");
		when(deliveryAccountBindingService.getOne(any(), eq(false))).thenReturn(binding);

		service.unbindMallUser("staff-1", "admin");

		assertThat(binding.getStatus()).isEqualTo(DeliveryAccountBinding.STATUS_UNBOUND);
		verify(deliveryAccountBindingService).updateById(binding);
		verify(deliveryAccessGuard).revokeDeliverySessions("sys-user-1");
	}

	@Test
	void unbindMallUserIsRejectedWhenNotBound() {
		givenStaff("staff-1", "sys-user-1");
		when(deliveryAccountBindingService.getOne(any(), eq(false))).thenReturn(null);
		assertThatThrownBy(() -> service.unbindMallUser("staff-1", "admin"))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "该配送员未绑定商城账号");
	}

	@Test
	void grantQualificationInvalidatesCacheAndRevokeKicksSessions() {
		givenStaff("staff-1", "sys-user-1");
		when(remoteSysUserService.changeRoleByCode("sys-user-1", DeliveryStaffServiceImpl.DELIVERY_ROLE_CODE, true))
			.thenReturn(true);
		when(remoteSysUserService.changeRoleByCode("sys-user-1", DeliveryStaffServiceImpl.DELIVERY_ROLE_CODE, false))
			.thenReturn(true);

		service.changeQualification("staff-1", true, "admin");
		verify(deliveryAccessGuard).invalidateQualificationCache("sys-user-1");

		service.changeQualification("staff-1", false, "admin");
		verify(deliveryAccessGuard).revokeDeliverySessions("sys-user-1");
	}

	private void givenNormalSysUser() {
		SysUser sysUser = new SysUser();
		sysUser.setId("sys-user-1");
		sysUser.setStatus("0");
		sysUser.setNickname("张三");
		sysUser.setPhone("13800000000");
		when(remoteSysUserService.getUserById("sys-user-1")).thenReturn(sysUser);
	}

	private DeliveryOnboardDTO onboardDto(String userId, boolean grantQualification) {
		DeliveryOnboardDTO dto = new DeliveryOnboardDTO();
		dto.setUserId(userId);
		dto.setGrantQualification(grantQualification);
		return dto;
	}

}
