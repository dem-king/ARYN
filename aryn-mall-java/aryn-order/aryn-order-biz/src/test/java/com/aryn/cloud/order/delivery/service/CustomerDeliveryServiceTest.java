package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskCustomerVO;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerDeliveryServiceTest {

	@Test
	void exposesPublicProgressWithoutPhoneOrInternalException() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryEvidenceMapper evidenceMapper = mock(OrderDeliveryEvidenceMapper.class);
		RemoteMaterialAccessService materialAccessService = mock(RemoteMaterialAccessService.class);
		OrderDeliveryTask task = new OrderDeliveryTask().setId("task-1")
			.setOrderId("order-1")
			.setStatus("DELIVERING")
			.setAssigneeName("配送员张三")
			.setAssigneeMobile("13800000000")
			.setExceptionSummary("内部异常说明")
			.setTenantId("tenant-1");
		when(taskMapper.selectCustomerTask("tenant-1", "member-1", "order-1")).thenReturn(task);
		when(evidenceMapper.selectList(any())).thenReturn(List.of(
			new OrderDeliveryEvidence().setId("evidence-1").setEvidenceType("DELIVERED").setSortNo(1)));
		CustomerDeliveryService service = new CustomerDeliveryService(taskMapper, evidenceMapper,
			materialAccessService);

		DeliveryTaskCustomerVO view = withUser(() -> service.getByOrder("order-1"));

		assertThat(view.getAssigneeName()).isEqualTo("配送员张三");
		assertThat(view.toString()).doesNotContain("13800000000", "内部异常说明");
		assertThat(view.getEvidences()).extracting("id").containsExactly("evidence-1");
		verify(taskMapper).selectCustomerTask("tenant-1", "member-1", "order-1");
	}

	private <T> T withUser(java.util.function.Supplier<T> action) {
		ArynUser user = new ArynUser();
		user.setUserId("member-1");
		user.setTenantId("tenant-1");
		try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
			security.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOC)).thenReturn(user);
			return action.get();
		}
	}
}
