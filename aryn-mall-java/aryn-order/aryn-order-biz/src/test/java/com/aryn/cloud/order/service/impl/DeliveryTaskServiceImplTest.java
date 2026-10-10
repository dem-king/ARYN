package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryEvidence;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTaskLog;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.IDeliveryEvidenceService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskLogService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderItemService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskServiceImplTest {

	private DeliveryTaskServiceImpl service;

	private IDeliveryTaskLogService logService;

	private IDeliveryTripService tripService;

	private IDeliveryEvidenceService evidenceService;

	private IDeliveryStaffService staffService;

	private IDeliveryTaskItemService itemService;

	private IOrderItemService orderItemService;

	private OrderInfoMapper orderInfoMapper;

	private IOrderConfigService orderConfigService;

	private RemoteMaterialService remoteMaterialService;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTask.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTaskItem.class);
		// lambdaUpdate/lambdaQuery 的包装器在构造时就要表元信息；不显式初始化会
		// 依赖「别的测试类先跑过」这种偶然顺序
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTrip.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderInfo.class);
		tripService = mock(IDeliveryTripService.class);
		itemService = mock(IDeliveryTaskItemService.class);
		staffService = mock(IDeliveryStaffService.class);
		IDeliveryWarehouseConfigService warehouseService = mock(IDeliveryWarehouseConfigService.class);
		logService = mock(IDeliveryTaskLogService.class);
		evidenceService = mock(IDeliveryEvidenceService.class);
		orderItemService = mock(IOrderItemService.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderConfigService = mock(IOrderConfigService.class);
		// 默认打开自助拉单开关：多数用例测的是「允许」路径，收紧路径单独造桩
		when(orderConfigService.getConfig()).thenReturn(null);
		service = new DeliveryTaskServiceImpl(tripService, itemService, staffService, warehouseService, logService,
				evidenceService, orderItemService, orderConfigService, orderInfoMapper,
				mock(RocketMQTemplate.class));
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
	void backfillArriveRejectedWhenTaskNotWaitingArrive() {
		// 管理端补录只允许「待送达」：待派单/取货中不得直达送达，避免绕过配送流程
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.PICKING.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.backfillArriveByAdmin("task-1", List.of("m-1"), null, "admin-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "仅待送达的任务可由管理端补录送达凭证");
		verify(evidenceService, never()).save(any());
	}

	@Test
	void backfillArriveStillRequiresEvidenceImages() {
		// 补录与司机送达同口径：没有照片就没有交付依据，不得补录
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.backfillArriveByAdmin("task-1", List.of(), null, "admin-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "送达凭证图片数量必须为1至6张");
	}

	@Test
	void backfillArriveMarksArrivedAndSavesEvidence() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setTripId("trip-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(remoteMaterialService.mapUrlByIds(any())).thenReturn(Map.of("m-1", "http://files/m-1.jpg"));

		assertThat(service.backfillArriveByAdmin("task-1", List.of("m-1"), "漏点送达", "admin-1")).isTrue();

		ArgumentCaptor<DeliveryEvidence> evidenceCaptor = ArgumentCaptor.forClass(DeliveryEvidence.class);
		verify(evidenceService).save(evidenceCaptor.capture());
		assertThat(evidenceCaptor.getValue().getMaterialUrl()).isEqualTo("http://files/m-1.jpg");
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

	@Test
	void cancelTaskRefreshesTripTaskCountBeforeSettling() {
		// 既有 bug：取消任务只做收车判定、不重算趟次单数，
		// 管理端出车单列表的「订单数」会停留在取消前的旧值
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setTripId("trip-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		task.setAttemptNo(1);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(1L);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.cancel("task-1")).isTrue();

		// 单数按实际任务数回写
		verify(tripService).update(any());
		// 再按结清口径收车
		verify(tripService).completeIfAllTasksSettled("trip-1");
	}

	@Test
	void getTaskByOrderIdReturnsTaskWithStaffName() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setOrderId("order-1");
		task.setStaffId("staff-1");
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(staffService.mapStaffNames(any())).thenReturn(Map.of("staff-1", "张三"));

		DeliveryTask result = service.getTaskByOrderId("order-1");

		assertThat(result).isSameAs(task);
		assertThat(result.getStaffName()).isEqualTo("张三");
		assertThat(service.getTaskByOrderId(null)).isNull();
	}

	@Test
	void mapByOrderIdsBatchesTasksWithStaffName() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setOrderId("order-1");
		task.setStaffId("staff-1");
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(staffService.mapStaffNames(any())).thenReturn(Map.of("staff-1", "张三"));

		Map<String, DeliveryTask> result = service.mapByOrderIds(List.of("order-1", "order-2"));

		assertThat(result).containsOnlyKeys("order-1");
		assertThat(result.get("order-1").getStaffName()).isEqualTo("张三");
	}

	@Test
	void mapByOrderIdsReturnsEmptyMapWithoutOrderIds() {
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.mapByOrderIds(List.of())).isEmpty();
		verify(mapper, never()).selectList(any());
	}

	@Test
	void assignByOrderIdRejectsMissingTask() {
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(null);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.assignByOrderId("order-1", "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "配送任务不存在，无法派单");
	}

	@Test
	void assignByOrderIdRejectsAssignedTask() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThatThrownBy(() -> service.assignByOrderId("order-1", "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "配送任务已派单或已结束，请到配送任务页查看");
	}

	@Test
	void assignByOrderIdCreatesSingleTaskTrip() {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setTaskNo("DT-1");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setTenantId("t1");
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		when(mapper.update(any(), any())).thenReturn(1);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("u1");
		staff.setStaffName("张三");
		staff.setTenantId("t1");
		staff.setStatus("1");
		when(staffService.getById("staff-1")).thenReturn(staff);
		// 司机当前没有未出车的趟次：本次派单新建一张出车单
		when(tripService.getOne(any())).thenReturn(null);

		service.assignByOrderId("order-1", "staff-1");

		ArgumentCaptor<DeliveryTrip> tripCaptor = ArgumentCaptor.forClass(DeliveryTrip.class);
		verify(tripService).save(tripCaptor.capture());
		assertThat(tripCaptor.getValue().getStaffId()).isEqualTo("staff-1");
		assertThat(tripCaptor.getValue().getTaskCount()).isEqualTo(1);
		verify(mapper).update(any(), any());
		verify(logService).save(any());
	}

	@Test
	void assignByOrderIdMergesIntoWaitingTripInsteadOfCreatingNewOne() {
		// 司机今天要送的货装同一趟车：他还没出车时新派的单并入已有趟次，
		// 否则派 5 次就有 5 张出车单，工作台还只看得到最后一张
		DeliveryTask task = new DeliveryTask();
		task.setId("task-2");
		task.setTaskNo("DT-2");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setTenantId("t1");
		DeliveryTask existingTask = new DeliveryTask();
		existingTask.setId("task-1");
		existingTask.setTripId("trip-1");
		existingTask.setSortNo(1);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		// 第一次 selectOne 查订单任务，最后一次查趟内最大 sortNo
		when(mapper.selectOne(any())).thenReturn(task, existingTask);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task, existingTask);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(2L);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("u1");
		staff.setStaffName("张三");
		staff.setTenantId("t1");
		staff.setStatus("1");
		when(staffService.getById("staff-1")).thenReturn(staff);
		DeliveryTrip waitingTrip = new DeliveryTrip();
		waitingTrip.setId("trip-1");
		waitingTrip.setStaffId("staff-1");
		waitingTrip.setStatus(DeliveryTripStatusEnum.WAITING_LOAD.getCode());
		waitingTrip.setTaskCount(1);
		when(tripService.getOne(any())).thenReturn(waitingTrip);

		String tripId = service.assignByOrderId("order-2", "staff-1");

		// 不新建出车单，落在同一张在途趟次上
		assertThat(tripId).isEqualTo("trip-1");
		verify(tripService, never()).save(any());
		// 单数按实际任务数回写（并入后从 1 变 2）
		verify(tripService).update(any());
		verify(logService).save(any());
	}

	@Test
	void reassignMovesTaskOntoNewStaffTrip() {
		// 改派要连带换趟次：只改 staffId 而把 tripId 留在原司机车上，
		// 这单在新司机的工作台不会出现（工作台按司机趟次组织）
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setStaffId("staff-old");
		task.setTripId("trip-old");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setTenantId("t1");
		task.setVersion(0);
		task.setAttemptNo(1);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectById("task-1")).thenReturn(task);
		when(mapper.selectOne(any())).thenReturn((DeliveryTask) null);
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(1L);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-new");
		staff.setUserId("u2");
		staff.setStaffName("李四");
		staff.setTenantId("t1");
		staff.setStatus("1");
		when(staffService.getById("staff-new")).thenReturn(staff);
		DeliveryTrip newTrip = new DeliveryTrip();
		newTrip.setId("trip-new");
		newTrip.setStatus(DeliveryTripStatusEnum.WAITING_LOAD.getCode());
		when(tripService.getOne(any())).thenReturn(newTrip);

		assertThat(service.reassign("task-1", "staff-new")).isTrue();

		verify(tripService, never()).save(any());
		// 原趟次少一单、新趟次多一单，两趟的单数都要重算
		verify(tripService, times(2)).update(any());
		verify(tripService).completeIfAllTasksSettled("trip-old");
		verify(logService).save(any());
	}

	@Test
	void assignMergesIntoLoadingTripDirectlyAsPicking() {
		// 司机已点「开始配货」时并入的新单必须直接落「配货中」：
		// depart 只把配货中的任务带出去，留在待取货的新单永远送不出去
		DeliveryTask task = new DeliveryTask();
		task.setId("task-9");
		task.setTaskNo("DT-9");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setTenantId("t1");
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(2L);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("u1");
		staff.setStaffName("张三");
		staff.setTenantId("t1");
		staff.setStatus("1");
		when(staffService.getById("staff-1")).thenReturn(staff);
		DeliveryTrip loadingTrip = new DeliveryTrip();
		loadingTrip.setId("trip-1");
		loadingTrip.setStaffId("staff-1");
		loadingTrip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getOne(any())).thenReturn(loadingTrip);

		service.assignByOrderId("order-9", "staff-1");

		// 派单日志的目标状态即任务落库状态
		ArgumentCaptor<DeliveryTaskLog> logCaptor = ArgumentCaptor.forClass(DeliveryTaskLog.class);
		verify(logService).save(logCaptor.capture());
		assertThat(logCaptor.getValue().getToStatus()).isEqualTo(DeliveryTaskStatusEnum.PICKING.getCode());
	}

	@Test
	void assignMergesIntoDeliveringTripAsPicking() {
		// 一车一张单：司机已在配送路上，新派的货并入这趟车（不再另开一单）。
		// 落点是「配货中」而非「待送达」——车开出去不代表货已经在车上，
		// 司机必须按清单把这单的货配齐、再次出发，订单才转待收货。
		DeliveryTask task = new DeliveryTask();
		task.setId("task-9");
		task.setTaskNo("DT-9");
		task.setOrderId("order-9");
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setTenantId("t1");
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectOne(any())).thenReturn(task);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(task);
		when(mapper.selectList(any())).thenReturn(List.of(task));
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(2L);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId("staff-1");
		staff.setUserId("u1");
		staff.setStaffName("张三");
		staff.setTenantId("t1");
		staff.setStatus("1");
		when(staffService.getById("staff-1")).thenReturn(staff);
		DeliveryTrip deliveringTrip = new DeliveryTrip();
		deliveringTrip.setId("trip-1");
		deliveringTrip.setTripNo("TR-1");
		deliveringTrip.setStaffId("staff-1");
		deliveringTrip.setStatus(DeliveryTripStatusEnum.DELIVERING.getCode());
		when(tripService.getOne(any())).thenReturn(deliveringTrip);

		String tripId = service.assignByOrderId("order-9", "staff-1");

		// 并入既有趟次，不新建
		assertThat(tripId).isEqualTo("trip-1");
		verify(tripService, never()).save(any());
		ArgumentCaptor<DeliveryTaskLog> logCaptor = ArgumentCaptor.forClass(DeliveryTaskLog.class);
		verify(logService).save(logCaptor.capture());
		assertThat(logCaptor.getValue().getToStatus()).isEqualTo(DeliveryTaskStatusEnum.PICKING.getCode());
	}

	@Test
	void pullOrdersAcceptsDeliveringTripButKeepsTaskPicking() {
		// 一车一张单：车已开出（配送中）也能继续加货 —— 货陆续装车、司机回车取货都属正常。
		// 但加进来的单落「配货中」：司机按清单配齐后再次出发才转待收货。
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setTripNo("TR-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.DELIVERING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(1L);
		DeliveryTask existing = new DeliveryTask();
		existing.setId("task-1");
		existing.setOrderId("order-1");
		existing.setOrderNo("ORD-1");
		existing.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		existing.setAttemptNo(1);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(existing);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		int pulled = service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1"));

		assertThat(pulled).isEqualTo(1);
		ArgumentCaptor<DeliveryTaskLog> logCaptor = ArgumentCaptor.forClass(DeliveryTaskLog.class);
		verify(logService).save(logCaptor.capture());
		assertThat(logCaptor.getValue().getToStatus()).isEqualTo(DeliveryTaskStatusEnum.PICKING.getCode());
	}

	@Test
	void pullOrdersRejectsCompletedTrip() {
		// 已收车的趟次不能再加货（车已经回库结单）
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setStatus(DeliveryTripStatusEnum.COMPLETED.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);

		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "出车单已收车，无法追加订单");
	}

	@Test
	void pullOrdersRejectsOtherStaffTrip() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-2");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);

		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "无权操作该出车单");
	}

	@Test
	void pullOrdersRejectsEmptySelection() {
		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of()))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "请至少选择一个订单");
	}

	@Test
	void pullOrdersRejectedWhenSelfPullDisabledAndOrderUnassigned() {
		// 租户关掉自助拉单后，未派送给任何人的散单必须由管理端派单：构造请求也不能绕过
		OrderConfig config = new OrderConfig();
		config.setDriverSelfPullUnassigned("0");
		when(orderConfigService.getConfig()).thenReturn(config);
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		// 订单没有配送任务（未派送）
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg",
						"当前租户未开放司机自助拉单，订单[ORD-1]需由管理端派单");
	}

	@Test
	void pullOrdersAllowedWhenSelfPullDisabledButTaskAssignedToSelf() {
		// 关掉自助拉单只挡「未派送」：管理端已派给本人的任务仍要能拉进本趟
		OrderConfig config = new OrderConfig();
		config.setDriverSelfPullUnassigned("0");
		when(orderConfigService.getConfig()).thenReturn(config);
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(1L);
		DeliveryTask mineOwn = new DeliveryTask();
		mineOwn.setId("task-1");
		mineOwn.setOrderId("order-1");
		mineOwn.setOrderNo("ORD-1");
		mineOwn.setStaffId("staff-1");
		mineOwn.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		mineOwn.setAttemptNo(1);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(mineOwn);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		assertThat(service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1"))).isEqualTo(1);
	}

	@Test
	void pullOrdersRejectedWhenOrderClaimedByOtherStaff() {
		// 开关打开也不允许抢单：任务已派给别人的订单拉不进来
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		DeliveryTask othersTask = new DeliveryTask();
		othersTask.setId("task-1");
		othersTask.setOrderNo("ORD-1");
		othersTask.setStaffId("staff-2");
		othersTask.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(othersTask);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单[ORD-1]已被其他配送员接单");
	}

	@Test
	void selfPullUnassignedDefaultsToAllowed() {
		// 未配置即放行：配置表异常不能把司机彻底堵死
		when(orderConfigService.getConfig()).thenReturn(null);
		assertThat(service.isDriverSelfPullUnassignedAllowed()).isTrue();

		OrderConfig config = new OrderConfig();
		config.setDriverSelfPullUnassigned("0");
		when(orderConfigService.getConfig()).thenReturn(config);
		assertThat(service.isDriverSelfPullUnassignedAllowed()).isFalse();

		config.setDriverSelfPullUnassigned("1");
		when(orderConfigService.getConfig()).thenReturn(config);
		assertThat(service.isDriverSelfPullUnassignedAllowed()).isTrue();
	}

	@Test
	void listPullCandidatesSkipsUnassignedPoolWhenSelfPullDisabled() {
		// 关掉开关后空 source（历史「临时新增」口径）也不再并入未派送池，
		// 否则它会变成绕过开关的后门
		OrderConfig config = new OrderConfig();
		config.setDriverSelfPullUnassigned("0");
		when(orderConfigService.getConfig()).thenReturn(config);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of());

		List<com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO> candidates
				= service.listPullCandidates("trip-1", "staff-1", null, null);

		assertThat(candidates).isEmpty();
		// 未派送订单压根不该被查
		verify(orderInfoMapper, never()).selectList(any());
	}

	@Test
	void pullOrdersRejectsCompletedTask() {
		// 已完成/已取消的单不该重新上车
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		// 本趟已有订单为空；按订单查到的任务已完成
		when(mapper.selectList(any())).thenReturn(List.of());
		DeliveryTask done = new DeliveryTask();
		done.setId("task-1");
		done.setOrderNo("ORD-1");
		done.setStatus(DeliveryTaskStatusEnum.ARRIVED.getCode());
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(done);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		assertThatThrownBy(() -> service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单[ORD-1]当前状态不允许加入出车单");
	}

	@Test
	void pullOrdersMovesExistingTaskIntoLoadingTripAsPicking() {
		// 拉「挂在别趟、但已派给本人」的任务进来：改属本趟并把状态对齐到本趟进度，
		// 否则停在待取货的单永远送不出去（depart 只带配货中的任务）。
		// 注意 staffId 必须是本人：已派给别人的单属于抢单，拉不进来
		//（见 pullOrdersRejectedWhenOrderClaimedByOtherStaff）。
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setTripNo("TR-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setWarehouseAddress("仓址");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		// 本趟已有订单为空；按订单查到的任务在别的趟上
		when(mapper.selectList(any())).thenReturn(List.of());
		when(mapper.update(any(), any())).thenReturn(1);
		when(mapper.selectCount(any())).thenReturn(1L);
		DeliveryTask existing = new DeliveryTask();
		existing.setId("task-1");
		existing.setOrderId("order-1");
		existing.setOrderNo("ORD-1");
		existing.setStaffId("staff-1");
		existing.setTripId("trip-other");
		existing.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		existing.setAttemptNo(1);
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(existing);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("ORD-1");
		order.setTenantId("t1");
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);

		int pulled = service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1"));

		assertThat(pulled).isEqualTo(1);
		verify(mapper).update(any(), any());
		// 原趟次要少一单并做收车判定
		verify(tripService).completeIfAllTasksSettled("trip-other");
		ArgumentCaptor<DeliveryTaskLog> logCaptor = ArgumentCaptor.forClass(DeliveryTaskLog.class);
		verify(logService).save(logCaptor.capture());
		assertThat(logCaptor.getValue().getAction()).isEqualTo("PULL_INTO_TRIP");
		assertThat(logCaptor.getValue().getToStatus()).isEqualTo(DeliveryTaskStatusEnum.PICKING.getCode());
	}

	@Test
	void pullOrdersSkipsOrdersAlreadyInTrip() {
		// 本趟已有的订单不重复拉（司机可能连点两次）
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setTenantId("t1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(tripService.getById("trip-1")).thenReturn(trip);
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		DeliveryTask inTrip = new DeliveryTask();
		inTrip.setId("task-in");
		inTrip.setOrderId("order-1");
		when(mapper.selectList(any())).thenReturn(List.of(inTrip));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);

		assertThat(service.pullOrdersIntoTrip("trip-1", "staff-1", List.of("order-1"))).isZero();
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void listPullCandidatesReturnsMineAndExcludesOnesAlreadyInTrip() {
		// 「今日已有未完成」= 派给我但不在本趟的未完成任务；
		// 已在本趟的不能再出现（司机看的是「还能拉什么」）
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		DeliveryTask inTrip = new DeliveryTask();
		inTrip.setId("task-a");
		inTrip.setOrderId("order-in-trip");
		DeliveryTask elsewhere = new DeliveryTask();
		elsewhere.setId("task-b");
		elsewhere.setOrderId("order-elsewhere");
		elsewhere.setOrderNo("ORD-B");
		elsewhere.setStaffId("staff-1");
		elsewhere.setTripId("trip-other");
		elsewhere.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		// 第一次 selectList 查本趟已有订单，第二次查我的未完成任务
		when(mapper.selectList(any())).thenReturn(List.of(inTrip), List.of(elsewhere));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of());
		DeliveryTrip otherTrip = new DeliveryTrip();
		otherTrip.setTripNo("TR-OTHER");
		when(tripService.getById("trip-other")).thenReturn(otherTrip);

		List<com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO> candidates
				= service.listPullCandidates("trip-1", "staff-1", "MINE", null);

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.getOrderId()).isEqualTo("order-elsewhere");
			assertThat(candidate.getSource()).isEqualTo("MINE");
			// 要告诉司机「这单现在挂在哪趟车上」
			assertThat(candidate.getTripNo()).isEqualTo("TR-OTHER");
		});
	}

	@Test
	void listPullCandidatesDoesNotOfferOrdersClaimedByOtherStaff() {
		// 未派送候选里，已被别人接走的单不能给当前司机拉（司机自助拉单不是抢单）。
		// 两次 selectList：先查本趟已有订单（空），再按订单批量查任务（返回别人的任务）
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		DeliveryTask otherStaffTask = new DeliveryTask();
		otherStaffTask.setId("task-x");
		otherStaffTask.setOrderId("order-taken");
		otherStaffTask.setStaffId("staff-2");
		otherStaffTask.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(otherStaffTask));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo mine = new OrderInfo();
		mine.setId("order-mine");
		mine.setOrderNo("ORD-MINE");
		mine.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		mine.setDeliveryWay("3");
		OrderInfo taken = new OrderInfo();
		taken.setId("order-taken");
		taken.setOrderNo("ORD-TAKEN");
		taken.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		taken.setDeliveryWay("3");
		// 当前司机没有被谁接走的标记时，应能拉只属于自己的那单
		when(orderInfoMapper.selectList(any())).thenReturn(List.of(mine, taken));

		List<com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO> candidates
				= service.listPullCandidates("trip-1", "staff-1", "UNASSIGNED", null);

		assertThat(candidates).extracting("orderId").containsExactly("order-mine");
	}

	@Test
	void listPullCandidatesFiltersByKeyword() {
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any())).thenReturn(List.of());
		DeliveryTask match = new DeliveryTask();
		match.setId("task-1");
		match.setOrderId("order-1");
		match.setOrderNo("ORD-1001");
		match.setRecipientName("张三");
		match.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		DeliveryTask other = new DeliveryTask();
		other.setId("task-2");
		other.setOrderId("order-2");
		other.setOrderNo("ORD-2002");
		other.setRecipientName("李四");
		other.setStatus(DeliveryTaskStatusEnum.WAITING_PICK.getCode());
		when(mapper.selectList(any())).thenReturn(List.of(), List.of(match, other));
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		when(orderItemService.list(any(Wrapper.class))).thenReturn(List.of());

		List<com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO> candidates
				= service.listPullCandidates("trip-1", "staff-1", "MINE", "张三");

		assertThat(candidates).extracting("orderNo").containsExactly("ORD-1001");
	}

	@Test
	void listPullCandidatesReturnsEmptyWithoutTripOrStaff() {
		assertThat(service.listPullCandidates(null, "staff-1", null, null)).isEmpty();
		assertThat(service.listPullCandidates("trip-1", " ", null, null)).isEmpty();
	}

	@Test
	void listPullCandidatesIncludesCashOnDeliveryOrders() {
		// 货到付款单的 pay_status 恒为 0（钱送达时才收），它恰恰是司机必须上门的那类单。
		// 曾按 pay_status=1 过滤，导致 COD 单在「未派送订单」里全部消失。
		//
		// Mapper 是 mock，无法真的执行 SQL，所以这里分两半：
		// 1）喂一条 COD 单进服务，验证它能产出候选行（含 paymentType 供前端标「货到付款」）；
		// 2）静态断言查询条件里没有 pay_status —— 这一半才是防回归的关键，
		//    因为 mock 会把「加了过滤条件」这个错误完全掩盖掉（见
		//    mybatis-plus-wrapper-lazy-param-binding：eq 是惰性绑定，mock 不渲染即不生效）
		DeliveryTaskMapper mapper = mock(DeliveryTaskMapper.class);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of());
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		OrderInfo cod = new OrderInfo();
		cod.setId("order-cod");
		cod.setOrderNo("ORD-COD");
		cod.setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		cod.setDeliveryWay("4");
		cod.setPaymentType("3");
		cod.setPayStatus("0");
		when(orderInfoMapper.selectList(any())).thenReturn(List.of(cod));

		List<com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO> candidates
				= service.listPullCandidates("trip-1", "staff-1", "UNASSIGNED", null);

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.getOrderId()).isEqualTo("order-cod");
			// 前端据此给司机标「货到付款」，提醒送达时要收款
			assertThat(candidate.getPaymentType()).isEqualTo("3");
		});

		// 未派送候选的查询条件：只认「待发货 + 商城/内部配送」，不得混入付款语义
		String serviceSource = readServiceSource();
		String unassignedQuery = serviceSource.substring(
				serviceSource.indexOf("未派送 = 本租户待发货"),
				serviceSource.indexOf("orderByDesc(OrderInfo::getCreateTime)"));
		assertThat(unassignedQuery)
				.contains("OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()")
				.doesNotContain("getPayStatus");
	}

	/**
	 * 读取本服务的源码，用于静态断言查询条件（mock 无法覆盖 SQL 语义的场合）。
	 */
	private String readServiceSource() {
		try {
			return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
					"src/main/java/com/aryn/cloud/order/service/impl/DeliveryTaskServiceImpl.java")),
					java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.IOException e) {
			throw new IllegalStateException("读取服务源码失败", e);
		}
	}

}
