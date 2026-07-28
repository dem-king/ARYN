package com.aryn.cloud.order.delivery.job;

import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.service.DeliveryEvidenceBindingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryEvidenceBindingRecoveryJobTest {

	@Test
	void retriesPendingBindingFromDeliveryLogRequestId() {
		OrderDeliveryEvidenceMapper evidenceMapper = mock(OrderDeliveryEvidenceMapper.class);
		OrderDeliveryTaskLogMapper logMapper = mock(OrderDeliveryTaskLogMapper.class);
		DeliveryEvidenceBindingService bindingService = mock(DeliveryEvidenceBindingService.class);
		OrderDeliveryEvidence evidence = new OrderDeliveryEvidence().setTaskId("task-1").setTenantId("tenant-1");
		when(evidenceMapper.selectPendingBindings(100)).thenReturn(List.of(evidence));
		when(logMapper.selectLatestEvidenceAction("tenant-1", "task-1"))
			.thenReturn(new OrderDeliveryTaskLog().setRequestId("request-1"));
		DeliveryEvidenceBindingRecoveryJob job =
			new DeliveryEvidenceBindingRecoveryJob(evidenceMapper, logMapper, bindingService);

		job.recoverPendingBindings();

		verify(bindingService).confirmBinding("tenant-1", "task-1:request-1", "task-1");
	}

}
