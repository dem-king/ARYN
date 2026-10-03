package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTripServiceImplTest {

	private DeliveryTripServiceImpl service;

	private DeliveryTripMapper mapper;

	private IDeliveryTaskService taskService;

	private IDeliveryTaskItemService itemService;

	private IDeliveryWarehouseConfigService warehouseService;

	private IDeliveryStaffService staffService;

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
		service = new DeliveryTripServiceImpl(taskService, itemService, mock(IOrderDeliveryStateService.class),
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
