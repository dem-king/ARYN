package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryPickupSummaryVO;
import com.aryn.cloud.order.mapper.DeliveryTripMapper;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import com.aryn.cloud.order.service.IOrderDeliveryStateService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTripServiceImplTest {

	private DeliveryTripServiceImpl service;

	private DeliveryTripMapper mapper;

	private IDeliveryTaskService taskService;

	private IDeliveryTaskItemService itemService;

	private IDeliveryWarehouseConfigService warehouseService;

	private IDeliveryStaffService staffService;

	private IOrderDeliveryStateService orderDeliveryStateService;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTrip.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTask.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTaskItem.class);
		mapper = mock(DeliveryTripMapper.class);
		taskService = mock(IDeliveryTaskService.class);
		itemService = mock(IDeliveryTaskItemService.class);
		warehouseService = mock(IDeliveryWarehouseConfigService.class);
		staffService = mock(IDeliveryStaffService.class);
		orderDeliveryStateService = mock(IOrderDeliveryStateService.class);
		service = new DeliveryTripServiceImpl(taskService, itemService, orderDeliveryStateService,
				warehouseService, staffService);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
	}

	@Test
	void getTripDetailFillsDerivedCountsAndWarehouseName() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStatus("2");
		when(mapper.selectById("trip-1")).thenReturn(trip);

		DeliveryTask arrived = new DeliveryTask();
		arrived.setId("task-1");
		arrived.setArriveTime(LocalDateTime.now());
		DeliveryTask loading = new DeliveryTask();
		loading.setId("task-2");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(arrived, loading));
		when(itemService.listByTaskId("task-1")).thenReturn(List.of(item("1"), item("1")));
		when(itemService.listByTaskId("task-2")).thenReturn(List.of(item("0"), item("1"), item("0")));

		DeliveryWarehouseConfig config = new DeliveryWarehouseConfig();
		config.setWarehouseName("一号仓");
		when(warehouseService.getConfig()).thenReturn(config);

		DeliveryTrip result = service.getTripDetail("trip-1");

		assertThat(result.getTotalItemCount()).isEqualTo(5);
		assertThat(result.getPickedItemCount()).isEqualTo(3);
		assertThat(result.getArrivedTaskCount()).isEqualTo(1);
		assertThat(result.getWarehouseName()).isEqualTo("一号仓");
	}

	@Test
	void getTripDetailFillsStaffNameAndPickupSummary() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		when(mapper.selectById("trip-1")).thenReturn(trip);

		DeliveryTask first = new DeliveryTask();
		first.setId("task-1");
		first.setStaffId("staff-1");
		DeliveryTask second = new DeliveryTask();
		second.setId("task-2");
		second.setStaffId("staff-1");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(first, second));
		when(itemService.listByTaskId("task-1")).thenReturn(List.of(goodsItem("矿泉水", "550ml", 2)));
		when(itemService.listByTaskId("task-2")).thenReturn(List.of(goodsItem("矿泉水", "550ml", 3),
				goodsItem("压缩饼干", null, 1)));
		doAnswer(invocation -> {
			List<DeliveryTask> tasks = invocation.getArgument(0);
			tasks.forEach(task -> task.setStaffName("张三"));
			return null;
		}).when(taskService).fillStaffName(any());
		when(staffService.mapStaffNames(any())).thenReturn(java.util.Map.of("staff-1", "张三"));
		when(warehouseService.getConfig()).thenReturn(null);

		DeliveryTrip result = service.getTripDetail("trip-1");

		// 详情页顶部展示出车单自身的配送员，任务表格展示任务行的
		assertThat(result.getStaffName()).isEqualTo("张三");
		assertThat(result.getTaskList()).allSatisfy(task -> assertThat(task.getStaffName()).isEqualTo("张三"));
		assertThat(result.getPickupSummary()).hasSize(2);
		DeliveryPickupSummaryVO merged = result.getPickupSummary().get(0);
		assertThat(merged.getSpuName()).isEqualTo("矿泉水");
		assertThat(merged.getSpecsInfo()).isEqualTo("550ml");
		assertThat(merged.getQuantity()).isEqualTo(5);
		DeliveryPickupSummaryVO noSpecs = result.getPickupSummary().get(1);
		assertThat(noSpecs.getSpuName()).isEqualTo("压缩饼干");
		assertThat(noSpecs.getQuantity()).isEqualTo(1);
	}

	@Test
	void pageWithStaffNameFillsNamesForListRows() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		Page<DeliveryTrip> page = new Page<>(1, 10);
		page.setRecords(List.of(trip));
		when(mapper.selectPage(any(), any())).thenReturn(page);
		when(staffService.mapStaffNames(any())).thenReturn(Map.of("staff-1", "张三"));

		Wrapper<DeliveryTrip> query = Wrappers.<DeliveryTrip>lambdaQuery().eq(DeliveryTrip::getId, "trip-1");
		IPage<DeliveryTrip> result = service.pageWithStaffName(new Page<>(1, 10), query);

		assertThat(result.getRecords()).singleElement()
				.satisfies(row -> assertThat(row.getStaffName()).isEqualTo("张三"));
	}

	@Test
	void getActiveTripReusesDetailAssembly() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStatus("2");
		// getOne 走 baseMapper.selectOne(wrapper, throwEx)，mock 接口不会执行 default 实现，需直接桩两参方法
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(trip);
		when(mapper.selectById("trip-1")).thenReturn(trip);
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of());
		DeliveryWarehouseConfig config = new DeliveryWarehouseConfig();
		config.setWarehouseName("一号仓");
		when(warehouseService.getConfig()).thenReturn(config);

		DeliveryTrip result = service.getActiveTrip("staff-1");

		assertThat(result).isNotNull();
		assertThat(result.getTotalItemCount()).isZero();
		assertThat(result.getArrivedTaskCount()).isZero();
		assertThat(result.getWarehouseName()).isEqualTo("一号仓");
	}

	@Test
	void getActiveTripReturnsNullWithoutStaffOrActiveTrip() {
		assertThat(service.getActiveTrip(null)).isNull();
		assertThat(service.getActiveTrip(" ")).isNull();

		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
		assertThat(service.getActiveTrip("staff-1")).isNull();
	}

	@Test
	void listActiveTripBriefsReturnsAllTripsWithOrderedStopsAndCounts() {
		// 订单分批派给同一司机时各自成趟，工作台必须一次给出全部在途趟次，
		// 否则司机只看得到最后一趟，前面的订单等于消失
		DeliveryTrip first = new DeliveryTrip();
		first.setId("trip-1");
		first.setTripNo("TR-1");
		first.setStatus("2");
		DeliveryTrip second = new DeliveryTrip();
		second.setId("trip-2");
		second.setTripNo("TR-2");
		second.setStatus("1");
		when(mapper.selectList(any())).thenReturn(List.of(first, second));

		DeliveryTask stopTwo = new DeliveryTask();
		stopTwo.setId("task-2");
		stopTwo.setTripId("trip-1");
		stopTwo.setSortNo(2);
		stopTwo.setOrderNo("ORD-2");
		stopTwo.setStatus("4");
		DeliveryTask stopOne = new DeliveryTask();
		stopOne.setId("task-1");
		stopOne.setTripId("trip-1");
		stopOne.setSortNo(1);
		stopOne.setOrderNo("ORD-1");
		stopOne.setStatus("5");
		stopOne.setArriveTime(LocalDateTime.now());
		DeliveryTask secondTripStop = new DeliveryTask();
		secondTripStop.setId("task-3");
		secondTripStop.setTripId("trip-2");
		secondTripStop.setSortNo(1);
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(stopOne, stopTwo, secondTripStop));

		DeliveryTaskItem picked = new DeliveryTaskItem();
		picked.setTaskId("task-1");
		picked.setPicked("1");
		DeliveryTaskItem unpicked = new DeliveryTaskItem();
		unpicked.setTaskId("task-2");
		unpicked.setPicked("0");
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of(picked, unpicked));

		DeliveryWarehouseConfig config = new DeliveryWarehouseConfig();
		config.setWarehouseName("一号仓");
		when(warehouseService.getConfig()).thenReturn(config);

		List<com.aryn.cloud.order.api.vo.DeliveryTripBriefVO> briefs = service.listActiveTripBriefs("staff-1");

		assertThat(briefs).hasSize(2);
		com.aryn.cloud.order.api.vo.DeliveryTripBriefVO tripOne = briefs.get(0);
		assertThat(tripOne.getTripNo()).isEqualTo("TR-1");
		assertThat(tripOne.getWarehouseName()).isEqualTo("一号仓");
		assertThat(tripOne.getTaskCount()).isEqualTo(2);
		assertThat(tripOne.getArrivedTaskCount()).isEqualTo(1);
		assertThat(tripOne.getTotalItemCount()).isEqualTo(2);
		assertThat(tripOne.getPickedItemCount()).isEqualTo(1);
		// 每趟的站点按 sortNo 排列，司机照此顺序装车与送货
		assertThat(tripOne.getTaskList()).extracting("id").containsExactly("task-1", "task-2");
		assertThat(briefs.get(1).getTaskList()).singleElement()
				.satisfies(stop -> assertThat(stop.getTaskNo()).isNull());
	}

	@Test
	void listActiveTripBriefsReturnsEmptyWithoutStaffOrTrips() {
		assertThat(service.listActiveTripBriefs(null)).isEmpty();
		assertThat(service.listActiveTripBriefs(" ")).isEmpty();

		when(mapper.selectList(any())).thenReturn(List.of());
		assertThat(service.listActiveTripBriefs("staff-1")).isEmpty();
		verify(taskService, never()).list(any(Wrapper.class));
	}

	@Test
	void adjustSortRejectsPartialTaskList() {
		// 司机端可能基于旧快照提交：只交部分任务会把未提交的留在原序号，路线静默错乱
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		when(mapper.selectById("trip-1")).thenReturn(trip);
		DeliveryTask first = new DeliveryTask();
		first.setId("task-1");
		DeliveryTask second = new DeliveryTask();
		second.setId("task-2");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(first, second));

		assertThatThrownBy(() -> service.adjustSort("trip-1", "staff-1", List.of("task-1")))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "送货顺序与当前趟次不一致，请刷新后重试");
		verify(taskService, never()).update(any(Wrapper.class));
	}

	@Test
	void adjustSortPersistsFullPermutationByIndex() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		when(mapper.selectById("trip-1")).thenReturn(trip);
		DeliveryTask first = new DeliveryTask();
		first.setId("task-1");
		DeliveryTask second = new DeliveryTask();
		second.setId("task-2");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(first, second));

		assertThat(service.adjustSort("trip-1", "staff-1", List.of("task-2", "task-1"))).isTrue();

		// 顺序按提交的排列落库：列表序号 1、2 分别对应 task-2、task-1
		verify(taskService, times(2)).update(any(Wrapper.class));
	}

	@Test
	void completeIfAllTasksSettledCompletesTripWithOnlyDeliveredTasks() {
		// 已送达即司机侧履约终点：任务送达后出车单必须能收车，
		// 不能等客户签收（否则「当前出车单」永远显示配送中）
		when(taskService.count(any(Wrapper.class))).thenReturn(2L, 0L);
		when(mapper.update(any(), any())).thenReturn(1);

		assertThat(service.completeIfAllTasksSettled("trip-1")).isTrue();
		// 条件更新带状态护栏，并发收车时只有一方生效
		verify(mapper).update(any(), any());
	}

	@Test
	void completeIfAllTasksSettledKeepsTripActiveWhenUnsettledTasksRemain() {
		when(taskService.count(any(Wrapper.class))).thenReturn(2L, 1L);

		assertThat(service.completeIfAllTasksSettled("trip-1")).isFalse();
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void completeIfAllTasksSettledSkipsEmptyTripAndConcurrentLoser() {
		assertThat(service.completeIfAllTasksSettled(null)).isFalse();
		assertThat(service.completeIfAllTasksSettled(" ")).isFalse();
		verify(mapper, never()).update(any(), any());

		// 出车单没有任何任务：不动作
		when(taskService.count(any(Wrapper.class))).thenReturn(0L);
		assertThat(service.completeIfAllTasksSettled("trip-1")).isFalse();

		// 并发（多单同时送达）时条件更新影响 0 行，视为未由本次收车
		when(taskService.count(any(Wrapper.class))).thenReturn(1L, 0L);
		when(mapper.update(any(), any())).thenReturn(0);
		assertThat(service.completeIfAllTasksSettled("trip-1")).isFalse();
	}

	@Test
	void departFromLoadingAdvancesTripAndPickingTasks() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setStatus(DeliveryTripStatusEnum.LOADING.getCode());
		when(mapper.selectById("trip-1")).thenReturn(trip);
		when(mapper.update(any(), any())).thenReturn(1);
		when(itemService.allPicked("trip-1")).thenReturn(true);
		DeliveryTask picking = new DeliveryTask();
		picking.setId("task-1");
		picking.setOrderId("order-1");
		picking.setStatus(DeliveryTaskStatusEnum.PICKING.getCode());
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(picking));

		assertThat(service.depart("trip-1", "staff-1")).isTrue();

		// 首次出发：趟次转配送中，配货中的任务转待送达并触发订单联动
		verify(mapper).update(any(), any());
		verify(taskService).update(any(Wrapper.class));
		verify(orderDeliveryStateService).markShippedOnPickUp(picking);
	}

	@Test
	void departTwiceIsIdempotentAndOnlyCarriesNewlyAttachedTasks() {
		// 一车一张单：车已开出后重复点「出发」不得报错，只把后来新并入的
		// 配货中任务带上路（首次那批早已是待送达，不该被再次联动）
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setStatus(DeliveryTripStatusEnum.DELIVERING.getCode());
		when(mapper.selectById("trip-1")).thenReturn(trip);
		DeliveryTask newTask = new DeliveryTask();
		newTask.setId("task-2");
		newTask.setOrderNo("ORD-2");
		newTask.setOrderId("order-2");
		newTask.setStatus(DeliveryTaskStatusEnum.PICKING.getCode());
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(newTask));
		when(itemService.allPickedByTask("task-2")).thenReturn(true);

		assertThat(service.depart("trip-1", "staff-1")).isTrue();

		// 已出发：不再重复改趟次状态
		verify(mapper, never()).update(any(), any());
		// 只对新并入的任务做联动
		verify(orderDeliveryStateService).markShippedOnPickUp(newTask);
	}

	@Test
	void departTwiceRejectsUnpickedNewlyAttachedTask() {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStaffId("staff-1");
		trip.setStatus(DeliveryTripStatusEnum.DELIVERING.getCode());
		when(mapper.selectById("trip-1")).thenReturn(trip);
		DeliveryTask newTask = new DeliveryTask();
		newTask.setId("task-2");
		newTask.setOrderNo("ORD-2");
		newTask.setStatus(DeliveryTaskStatusEnum.PICKING.getCode());
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(newTask));
		when(itemService.allPickedByTask("task-2")).thenReturn(false);

		assertThatThrownBy(() -> service.depart("trip-1", "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "订单[ORD-2]还有未确认取货的明细，无法出发");
		verify(orderDeliveryStateService, never()).markShippedOnPickUp(any());
	}

	private DeliveryTaskItem item(String picked) {
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setId("item-" + picked + System.nanoTime());
		item.setPicked(picked);
		return item;
	}

	private DeliveryTaskItem goodsItem(String spuName, String skuName, int quantity) {
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setId("item-" + spuName + System.nanoTime());
		item.setSpuName(spuName);
		item.setSkuName(skuName);
		item.setQuantity(quantity);
		return item;
	}

}
