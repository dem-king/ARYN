package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.service.IDeliveryEvidenceService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskLogService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryTaskServiceImplTest {

	private DeliveryTaskServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTask.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTaskItem.class);
		IDeliveryTripService tripService = mock(IDeliveryTripService.class);
		IDeliveryTaskItemService itemService = mock(IDeliveryTaskItemService.class);
		IDeliveryStaffService staffService = mock(IDeliveryStaffService.class);
		IDeliveryWarehouseConfigService warehouseService = mock(IDeliveryWarehouseConfigService.class);
		IDeliveryTaskLogService logService = mock(IDeliveryTaskLogService.class);
		IDeliveryEvidenceService evidenceService = mock(IDeliveryEvidenceService.class);
		service = new DeliveryTaskServiceImpl(tripService, itemService, staffService, warehouseService, logService,
				evidenceService, mock(RocketMQTemplate.class));
	}

	@Test
	void arriveRequiresAtLeastOneEvidenceImage() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStaffId("staff-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.arriveWithEvidence("task-1", "staff-1", List.of(), null))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "送达凭证图片数量必须为1至6张");
	}

	@Test
	void arriveRejectsDuplicateEvidenceImages() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStaffId("staff-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.arriveWithEvidence("task-1", "staff-1", List.of("m-1", "m-1"), null))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "送达凭证图片无效或重复");
	}

}
