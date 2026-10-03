package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryEvidence;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.service.IDeliveryEvidenceService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskLogService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import com.aryn.cloud.upms.api.remote.RemoteMaterialService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskServiceImplTest {

	private DeliveryTaskServiceImpl service;

	private IDeliveryTaskLogService logService;

	private IDeliveryTripService tripService;

	private IDeliveryEvidenceService evidenceService;

	private RemoteMaterialService remoteMaterialService;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTask.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTaskItem.class);
		tripService = mock(IDeliveryTripService.class);
		IDeliveryTaskItemService itemService = mock(IDeliveryTaskItemService.class);
		IDeliveryStaffService staffService = mock(IDeliveryStaffService.class);
		IDeliveryWarehouseConfigService warehouseService = mock(IDeliveryWarehouseConfigService.class);
		logService = mock(IDeliveryTaskLogService.class);
		evidenceService = mock(IDeliveryEvidenceService.class);
		service = new DeliveryTaskServiceImpl(tripService, itemService, staffService, warehouseService, logService,
				evidenceService, mock(RocketMQTemplate.class));
		remoteMaterialService = mock(RemoteMaterialService.class);
		ReflectionTestUtils.setField(service, "remoteMaterialService", remoteMaterialService);
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

	@Test
	void arriveSnapshotsMaterialUrlIntoEvidence() {
		// 送达凭证必须带素材URL快照，否则管理端与小程序只能渲染空图
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStaffId("staff-1");
		task.setTripId("trip-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(remoteMaterialService.mapUrlByIds(any())).thenReturn(Map.of("m-1", "http://files/m-1.jpg"));

		service.arriveWithEvidence("task-1", "staff-1", List.of("m-1"), null);

		ArgumentCaptor<DeliveryEvidence> evidenceCaptor = ArgumentCaptor.forClass(DeliveryEvidence.class);
		verify(evidenceService).save(evidenceCaptor.capture());
		assertThat(evidenceCaptor.getValue().getMaterialUrl()).isEqualTo("http://files/m-1.jpg");
		assertThat(evidenceCaptor.getValue().getEvidenceType()).isEqualTo("1");
		// 出车单收车判定挂在送达路径上：单卡送达后司机侧不该再显示「配送中」
		verify(tripService).completeIfAllTasksSettled("trip-1");
	}

	@Test
	void arriveStillSavesEvidenceWhenMaterialServiceFails() {
		// 素材服务不可用不阻断送达：凭证先行落库，URL 由 listEvidence 读取侧兜底
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStaffId("staff-1");
		task.setTripId("trip-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		doThrow(new RuntimeException("dubbo down")).when(remoteMaterialService).mapUrlByIds(any());

		service.arriveWithEvidence("task-1", "staff-1", List.of("m-1"), null);

		ArgumentCaptor<DeliveryEvidence> evidenceCaptor = ArgumentCaptor.forClass(DeliveryEvidence.class);
		verify(evidenceService).save(evidenceCaptor.capture());
		assertThat(evidenceCaptor.getValue().getMaterialId()).isEqualTo("m-1");
		verify(tripService).completeIfAllTasksSettled("trip-1");
	}

	@Test
	void listEvidenceBackfillsMissingUrlSnapshot() {
		// 存量凭证行只存了素材ID，读取时按素材ID回填URL，避免两端渲染空图
		DeliveryEvidence withUrl = new DeliveryEvidence();
		withUrl.setId("e-1");
		withUrl.setMaterialId("m-1");
		withUrl.setMaterialUrl("http://files/m-1.jpg");
		DeliveryEvidence withoutUrl = new DeliveryEvidence();
		withoutUrl.setId("e-2");
		withoutUrl.setMaterialId("m-2");
		when(evidenceService.list(any(Wrapper.class))).thenReturn(List.of(withUrl, withoutUrl));
		when(remoteMaterialService.mapUrlByIds(any())).thenReturn(Map.of("m-2", "http://files/m-2.jpg"));

		List<DeliveryEvidence> result = service.listEvidence("task-1");

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getMaterialUrl()).isEqualTo("http://files/m-1.jpg");
		assertThat(result.get(1).getMaterialUrl()).isEqualTo("http://files/m-2.jpg");
	}

	@Test
	void getProgressIncludesArriveEvidenceUrlsOnly() {
		// 买家侧订单详情的配送进度必须带回送达凭证 URL；异常凭证不暴露给客户
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setOrderId("order-1");
		task.setStatus(DeliveryTaskStatusEnum.SIGNED.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		DeliveryEvidence arrive = new DeliveryEvidence();
		arrive.setId("e-1");
		arrive.setEvidenceType("1");
		arrive.setMaterialId("m-1");
		arrive.setMaterialUrl("http://files/m-1.jpg");
		DeliveryEvidence exception = new DeliveryEvidence();
		exception.setId("e-2");
		exception.setEvidenceType("2");
		exception.setMaterialId("m-2");
		exception.setMaterialUrl("http://files/m-2.jpg");
		when(evidenceService.list(any(Wrapper.class))).thenReturn(List.of(arrive, exception));

		DeliveryProgressVO progress = service.getProgress("order-1");

		assertThat(progress).isNotNull();
		assertThat(progress.getEvidenceUrls()).containsExactly("http://files/m-1.jpg");
	}

	@Test
	void cancelWaitingAssignReturnsFalseWhenNoTask() {
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(null);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
		when(mapper.selectList(any())).thenReturn(List.of());
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.cancelWaitingAssignByOrderId("order-1")).isFalse();
	}

	@Test
	void cancelWaitingAssignIsIdempotentWhenAlreadyCanceled() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.CANCELED.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.cancelWaitingAssignByOrderId("order-1")).isTrue();
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void cancelWaitingAssignRejectsTaskAlreadyAssigned() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.cancelWaitingAssignByOrderId("order-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "配送已安排，无法取消");
		verify(mapper, never()).update(any(), any());
		verify(logService, never()).save(any());
	}

	@Test
	void cancelWaitingAssignClosesWaitingAssignTask() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		when(mapper.update(any(), any())).thenReturn(1);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.cancelWaitingAssignByOrderId("order-1")).isTrue();
		verify(mapper).update(any(), any());
		verify(logService).save(any());
	}

}
