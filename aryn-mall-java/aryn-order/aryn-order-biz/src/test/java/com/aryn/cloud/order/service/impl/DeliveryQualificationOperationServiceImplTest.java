
package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配送资格操作补偿测试：幂等键复用、条件认领、失败重试记录（R-07）
 */
class DeliveryQualificationOperationServiceImplTest {

	@BeforeAll
	static void initTableInfo() {
		// 单元测试无 MyBatis 环境，手动注册实体 lambda 缓存供 Wrappers 解析属性
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				DeliveryQualificationOperation.class);
	}

	private RemoteSysUserService remoteSysUserService;

	private DeliveryQualificationOperationServiceImpl service;

	@BeforeEach
	void setUp() {
		remoteSysUserService = mock(RemoteSysUserService.class);
		service = spy(new DeliveryQualificationOperationServiceImpl(remoteSysUserService));
	}

	private DeliveryQualificationOperation pendingOperation(String operation) {
		DeliveryQualificationOperation op = new DeliveryQualificationOperation();
		op.setId("op-1");
		op.setStaffId("staff-1");
		op.setSysUserId("sys-user-1");
		op.setRoleCode("delivery_staff");
		op.setOperation(operation);
		op.setStatus(DeliveryQualificationOperation.STATUS_PENDING);
		op.setIdempotentKey(operation + ":sys-user-1:delivery_staff");
		op.setRetryCount(0);
		return op;
	}

	@Test
	void createPendingInsertsWhenNoHistory() {
		doReturn(null).when(service).getOne(any(Wrapper.class), eq(false));
		doReturn(true).when(service).save(any(DeliveryQualificationOperation.class));

		DeliveryQualificationOperation op = service.createPending("staff-1", "sys-user-1", "delivery_staff",
				DeliveryQualificationOperation.OPERATION_GRANT, "admin");

		assertThat(op.getStatus()).isEqualTo(DeliveryQualificationOperation.STATUS_PENDING);
		assertThat(op.getIdempotentKey()).isEqualTo("GRANT:sys-user-1:delivery_staff");
		verify(service).save(any(DeliveryQualificationOperation.class));
	}

	@Test
	void createPendingReusesAndResetsExistingRecord() {
		DeliveryQualificationOperation existing = pendingOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		existing.setStatus(DeliveryQualificationOperation.STATUS_DONE);
		existing.setRetryCount(3);
		existing.setLastError("stale error");
		doReturn(existing).when(service).getOne(any(Wrapper.class), eq(false));
		doReturn(true).when(service).update(any(Wrapper.class));

		DeliveryQualificationOperation op = service.createPending("staff-1", "sys-user-1", "delivery_staff",
				DeliveryQualificationOperation.OPERATION_GRANT, "admin");

		// 复用同一条历史记录重置为待处理，避免重复行
		assertThat(op).isSameAs(existing);
		assertThat(existing.getStatus()).isEqualTo(DeliveryQualificationOperation.STATUS_PENDING);
		assertThat(existing.getRetryCount()).isZero();
		assertThat(existing.getLastError()).isNull();
		assertThat(existing.getNextRetryTime()).isNull();
		verify(service).update(any(Wrapper.class));
	}

	@Test
	void processPendingExecutesGrantAndMarksDone() {
		DeliveryQualificationOperation op = pendingOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		doReturn(op).when(service).getById("op-1");
		doReturn(true).when(service).update(any(Wrapper.class));
		when(remoteSysUserService.changeRoleByCode("sys-user-1", "delivery_staff", true)).thenReturn(true);

		assertThat(service.processPending("op-1")).isTrue();
		verify(remoteSysUserService).changeRoleByCode("sys-user-1", "delivery_staff", true);
		// 认领一次 + 标记完成一次
		verify(service, org.mockito.Mockito.times(2)).update(any(Wrapper.class));
	}

	@Test
	void processPendingRecordsErrorAndWaitsRetry() {
		DeliveryQualificationOperation op = pendingOperation(DeliveryQualificationOperation.OPERATION_REVOKE);
		doReturn(op).when(service).getById("op-1");
		doReturn(true).when(service).update(any(Wrapper.class));
		when(remoteSysUserService.changeRoleByCode("sys-user-1", "delivery_staff", false))
			.thenThrow(new RuntimeException("upms unavailable"));

		assertThat(service.processPending("op-1")).isFalse();
		// 认领 + 记录失败原因，等待指数退避重试
		verify(service, org.mockito.Mockito.times(2)).update(any(Wrapper.class));
	}

	@Test
	void processPendingIsNoopForDoneOperation() {
		DeliveryQualificationOperation op = pendingOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		op.setStatus(DeliveryQualificationOperation.STATUS_DONE);
		doReturn(op).when(service).getById("op-1");

		assertThat(service.processPending("op-1")).isTrue();
		verify(remoteSysUserService, never()).changeRoleByCode(anyString(), anyString(), eq(true));
	}

	@Test
	void processPendingReturnsFalseWhenClaimLostToOtherInstance() {
		DeliveryQualificationOperation op = pendingOperation(DeliveryQualificationOperation.OPERATION_GRANT);
		doReturn(op).when(service).getById("op-1");
		// 条件认领失败：其他实例正在处理，尚未完成
		doReturn(false).when(service).update(any(Wrapper.class));
		doReturn(op).when(service).getById("op-1");

		assertThat(service.processPending("op-1")).isFalse();
		verify(remoteSysUserService, never()).changeRoleByCode(anyString(), anyString(), eq(true));
	}

	@Test
	void listDueForRetryQueriesPendingOnly() {
		doReturn(java.util.List.of()).when(service).list(any(Wrapper.class));
		assertThat(service.listDueForRetry(20)).isEmpty();
		verify(service).list(any(Wrapper.class));
	}

}
