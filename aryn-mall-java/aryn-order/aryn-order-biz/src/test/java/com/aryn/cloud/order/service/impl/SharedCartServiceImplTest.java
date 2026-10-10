package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享购物车提交顺延靠港单测。
 *
 * <p>背景（2026-10-04）：整车的 vessel_call_id 是创建时刻的快照，而收集中会持续数天，
 * 靠港 ETD 过点后提交会被下单校验以「靠港计划不可用（不存在或已离港）」拒绝，且共享车
 * 没有切换靠港的出路，全员明细被扣死；成员端查找活动车有回退、加购照常，问题直到
 * 提交才暴露，更加隐蔽。提交时顺延到该船当前可用靠港（ETA 最早一班），无可用靠港时
 * 给可读报错引导先申报。
 */
class SharedCartServiceImplTest {

	private static final String TENANT = "tenant-1";

	private SharedCartMapper sharedCartMapper;
	private SharedCartItemMapper sharedCartItemMapper;
	private IOrderInfoService orderInfoService;
	private RemoteVesselService remoteVesselService;
	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		sharedCartMapper = mock(SharedCartMapper.class);
		sharedCartItemMapper = mock(SharedCartItemMapper.class);
		orderInfoService = mock(IOrderInfoService.class);
		remoteVesselService = mock(RemoteVesselService.class);
		service = new SharedCartServiceImpl(sharedCartMapper, mock(SharedCartMemberMapper.class),
				sharedCartItemMapper, mock(SharedCartImportMapper.class),
				mock(SharedCartImportRowMapper.class), orderInfoService);
		// @DubboReference 字段不走构造器，测试里反射注入
		ReflectionTestUtils.setField(service, "remoteVesselService", remoteVesselService);
		ReflectionTestUtils.setField(service, "remoteMallUserService", mock(RemoteMallUserService.class));
	}

	@Test
	void submitRetargetsCartToLatestAvailableCallWhenSnapshotExpired() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		// 创建时刻的靠港已过点：按 ID 取上下文返回 null
		when(remoteVesselService.getVesselCallContext(TENANT, "call-old")).thenReturn(null);
		when(remoteVesselService.resolveAvailableCall(TENANT, "vessel-1")).thenReturn(context("call-new"));
		when(sharedCartItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item()));
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(order);

		String orderId = service.confirmAndCreateOrder(TENANT, "user-1", "cart-1", null);

		assertThat(orderId).isEqualTo("order-1");
		ArgumentCaptor<CreateOrderDTO> dtoCaptor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(dtoCaptor.capture());
		assertThat(dtoCaptor.getValue().getVesselCallId()).isEqualTo("call-new");
		// 顺延要落库：整车记录随提交一起更新为新靠港
		ArgumentCaptor<SharedCart> cartCaptor = ArgumentCaptor.forClass(SharedCart.class);
		verify(sharedCartMapper).updateById(cartCaptor.capture());
		assertThat(cartCaptor.getValue().getVesselCallId()).isEqualTo("call-new");
	}

	@Test
	void submitFailsWithReadableErrorWhenVesselHasNoAvailableCall() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteVesselService.getVesselCallContext(TENANT, "call-old")).thenReturn(null);
		when(remoteVesselService.resolveAvailableCall(TENANT, "vessel-1")).thenReturn(null);

		assertThatThrownBy(() -> service.confirmAndCreateOrder(TENANT, "user-1", "cart-1", null))
			.isInstanceOfSatisfying(ArynBusinessException.class, ex ->
				assertThat(ex.getMsg()).contains("申报靠港"));
		// 不能让整车带着死靠港走到下单校验，报模糊的「不存在或已离港」
		verify(orderInfoService, never()).createOrder(any(CreateOrderDTO.class));
	}

	@Test
	void submitKeepsSnapshotCallWhenStillAvailable() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteVesselService.getVesselCallContext(TENANT, "call-old")).thenReturn(context("call-old"));
		when(sharedCartItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item()));
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(order);

		service.confirmAndCreateOrder(TENANT, "user-1", "cart-1", null);

		ArgumentCaptor<CreateOrderDTO> dtoCaptor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(dtoCaptor.capture());
		assertThat(dtoCaptor.getValue().getVesselCallId()).isEqualTo("call-old");
		verify(remoteVesselService, never()).resolveAvailableCall(any(), any());
	}

	private SharedCart collectingCart(String callId) {
		SharedCart cart = new SharedCart();
		cart.setId("cart-1");
		cart.setCartNo("SC-1");
		cart.setTenantId(TENANT);
		cart.setVesselId("vessel-1");
		cart.setVesselCallId(callId);
		cart.setOwnerUserId("user-1");
		cart.setConfirmerUserId("user-1");
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		cart.setExpiresAt(LocalDateTime.now().plusHours(2));
		cart.setDelFlag("0");
		return cart;
	}

	private SharedCartItem item() {
		SharedCartItem item = new SharedCartItem();
		item.setId("item-1");
		item.setCartId("cart-1");
		item.setUserId("user-2");
		item.setSpuId("spu-1");
		item.setSkuId("sku-1");
		item.setRequestedQuantity(2);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		return item;
	}

	private VesselContextDTO context(String callId) {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId("vessel-1");
		context.setVesselCallId(callId);
		return context;
	}

}
