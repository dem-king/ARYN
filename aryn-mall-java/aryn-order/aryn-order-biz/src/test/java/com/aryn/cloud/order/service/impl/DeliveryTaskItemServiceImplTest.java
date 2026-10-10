package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryTaskItemMapper;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskItemServiceImplTest {

	private DeliveryTaskItemServiceImpl service;

	private IOrderItemService orderItemService;

	private RemoteGoodsSpuService remoteGoodsSpuService;

	@BeforeEach
	void setUp() {
		orderItemService = mock(IOrderItemService.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		service = new DeliveryTaskItemServiceImpl(mock(IDeliveryTaskService.class), mock(IDeliveryTripService.class),
				orderItemService);
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", remoteGoodsSpuService);
	}

	@Test
	void fillCategoryNameResolvesCategoryThroughOrderItemAndSpu() {
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setOrderItemId("oi-1");

		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("oi-1");
		orderItem.setSpuId("spu-1");
		when(orderItemService.listByIds(List.of("oi-1"))).thenReturn(List.of(orderItem));

		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setCategoryName("生鲜/冷藏水产");
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1"))).thenReturn(List.of(spu));

		service.fillCategoryName(List.of(item));

		assertThat(item.getCategoryName()).isEqualTo("生鲜/冷藏水产");
	}

	@Test
	void fillCategoryNameKeepsNullWhenOrderItemMissingOrCategoryBlank() {
		DeliveryTaskItem linked = new DeliveryTaskItem();
		linked.setOrderItemId("oi-1");
		DeliveryTaskItem unlinked = new DeliveryTaskItem();

		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("oi-1");
		orderItem.setSpuId("spu-1");
		when(orderItemService.listByIds(List.of("oi-1"))).thenReturn(List.of(orderItem));

		GoodsSpu noCategory = new GoodsSpu();
		noCategory.setId("spu-1");
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1"))).thenReturn(List.of(noCategory));

		service.fillCategoryName(List.of(linked, unlinked));

		assertThat(linked.getCategoryName()).isNull();
		assertThat(unlinked.getCategoryName()).isNull();
	}

	@Test
	void fillCategoryNameSkipsRemoteCallWithoutLinkableItems() {
		DeliveryTaskItem item = new DeliveryTaskItem();

		service.fillCategoryName(List.of(item));

		verify(orderItemService, never()).listByIds(anyList());
		verify(remoteGoodsSpuService, never()).getSpuByIds(anyList());
	}

	@Test
	void batchPickRejectsItemsOutsideTheTrip() {
		// 配货汇总行一次提交多条明细：跨趟混入必须整批拒绝，
		// 否则会把别人车上的货确认成自己已取
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.LOADING.getCode(), "staff-1"));
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task));
		// 提交 2 条，只查回 1 条属于本趟
		when(batchMapper.selectList(any())).thenReturn(List.of(new DeliveryTaskItem()));

		assertThatThrownBy(() -> batchService.batchPick("trip-1", List.of("i1", "i2"), true, "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "存在不属于该出车单的取货明细");
	}

	@Test
	void batchPickRejectsWhenTripCompleted() {
		// 已完成（收车）的趟次不能再改取货状态
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.COMPLETED.getCode(), "staff-1"));

		assertThatThrownBy(() -> batchService.batchPick("trip-1", List.of("i1"), true, "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "出车单当前状态不允许确认取货");
	}

	@Test
	void batchPickInDeliveringTripAllowsOnlyNewlyAddedTasks() {
		// 车已开出后新并入的货（任务仍是配货中）必须能配，否则这单永远送不出去；
		// 但已出发的老货不能再改取货状态——出发后改装车事实是作弊
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.DELIVERING.getCode(), "staff-1"));
		DeliveryTask newlyAdded = new DeliveryTask();
		newlyAdded.setId("task-1");
		newlyAdded.setStatus(DeliveryTaskStatusEnum.PICKING.getCode());
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(newlyAdded));
		when(taskService.count(any(Wrapper.class))).thenReturn(1L);
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setId("i1");
		item.setTaskId("task-1");
		when(batchMapper.selectList(any())).thenReturn(List.of(item));
		when(batchMapper.update(any(), any())).thenReturn(1);

		assertThat(batchService.batchPick("trip-1", List.of("i1"), true, "staff-1")).isEqualTo(1);
	}

	@Test
	void batchPickInDeliveringTripRejectsAlreadyDepartedTasks() {
		// 老任务（待送达）的明细不许再改：货车已开走，取货状态不能再被翻转
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.DELIVERING.getCode(), "staff-1"));
		DeliveryTask departed = new DeliveryTask();
		departed.setId("task-1");
		departed.setStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode());
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(departed));
		// count 返回 0：没有任何一条明细挂在「配货中」的任务下
		when(taskService.count(any(Wrapper.class))).thenReturn(0L);
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setId("i1");
		item.setTaskId("task-1");
		when(batchMapper.selectList(any())).thenReturn(List.of(item));

		assertThatThrownBy(() -> batchService.batchPick("trip-1", List.of("i1"), true, "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "只能确认本趟新加的货，已出发的货不能改取货状态");
	}

	@Test
	void batchPickRejectsOtherStaffTrip() {
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.LOADING.getCode(), "staff-2"));

		assertThatThrownBy(() -> batchService.batchPick("trip-1", List.of("i1"), true, "staff-1"))
				.isInstanceOf(ArynBusinessException.class)
				.hasFieldOrPropertyWithValue("msg", "无权操作该出车单");
	}

	@Test
	void batchPickUpdatesItemsInTargetState() {
		prepareBatchService();
		when(tripService.getById("trip-1")).thenReturn(trip(DeliveryTripStatusEnum.LOADING.getCode(), "staff-1"));
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task));
		when(batchMapper.selectList(any())).thenReturn(List.of(new DeliveryTaskItem(), new DeliveryTaskItem()));
		when(batchMapper.update(any(), any())).thenReturn(2);

		assertThat(batchService.batchPick("trip-1", List.of("i1", "i2"), true, "staff-1")).isEqualTo(2);
		verify(batchMapper).update(any(), any());
	}

	@Test
	void batchPickWithoutItemIdsIsNoop() {
		prepareBatchService();

		assertThat(batchService.batchPick("trip-1", List.of(), true, "staff-1")).isZero();
		verify(batchMapper, never()).update(any(), any());
	}

	private DeliveryTaskItemServiceImpl batchService;

	private IDeliveryTaskService taskService;

	private IDeliveryTripService tripService;

	private DeliveryTaskItemMapper batchMapper;

	private void prepareBatchService() {
		// lambdaUpdate 的 set 是急切绑定，没有表元信息会抛「can not find lambda cache」
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTaskItem.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), DeliveryTask.class);
		taskService = mock(IDeliveryTaskService.class);
		tripService = mock(IDeliveryTripService.class);
		batchMapper = mock(DeliveryTaskItemMapper.class);
		batchService = new DeliveryTaskItemServiceImpl(taskService, tripService, orderItemService);
		ReflectionTestUtils.setField(batchService, "baseMapper", batchMapper);
	}

	private DeliveryTrip trip(String status, String staffId) {
		DeliveryTrip trip = new DeliveryTrip();
		trip.setId("trip-1");
		trip.setStatus(status);
		trip.setStaffId(staffId);
		return trip;
	}

}
