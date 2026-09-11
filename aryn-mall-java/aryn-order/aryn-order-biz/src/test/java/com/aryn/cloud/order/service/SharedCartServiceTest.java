package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享购物车权限与提交契约测试。
 */
class SharedCartServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String OWNER = "owner-1";

	private static final String MEMBER = "member-1";

	private static final String CART_ID = "cart-1";

	private SharedCartMapper cartMapper;

	private SharedCartMemberMapper memberMapper;

	private SharedCartItemMapper itemMapper;

	private IOrderInfoService orderInfoService;

	private RemoteShipProductProfileService remoteShipProductProfileService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		cartMapper = mock(SharedCartMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		itemMapper = mock(SharedCartItemMapper.class);
		orderInfoService = mock(IOrderInfoService.class);
		remoteShipProductProfileService = mock(RemoteShipProductProfileService.class);
		service = new SharedCartServiceImpl(cartMapper, memberMapper, itemMapper, orderInfoService);
		ReflectionTestUtils.setField(service, "remoteShipProductProfileService", remoteShipProductProfileService);
	}

	private SharedCart cart(String status) {
		SharedCart cart = new SharedCart();
		cart.setId(CART_ID);
		cart.setTenantId(TENANT);
		cart.setVesselId("vessel-1");
		cart.setVesselCallId("call-1");
		cart.setOwnerUserId(OWNER);
		cart.setConfirmerUserId(OWNER);
		cart.setStatus(status);
		return cart;
	}

	private SharedCartItem item(String itemId, String userId, String skuId, int quantity) {
		SharedCartItem item = new SharedCartItem();
		item.setId(itemId);
		item.setCartId(CART_ID);
		item.setUserId(userId);
		item.setSkuId(skuId);
		item.setSpuId("spu-" + skuId);
		item.setRequestedQuantity(quantity);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		return item;
	}

	@Test
	@DisplayName("发起人创建购物车并成为成员，状态进入收集中")
	void createInitializesOwnerMembership() {
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-1");
		SharedCart created = service.create(TENANT, OWNER, dto);
		assertEquals(SharedCart.STATUS_COLLECTING, created.getStatus());
		assertEquals(OWNER, created.getOwnerUserId());
		verify(memberMapper).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("成员只能修改自己添加的明细")
	void memberCannotEditOthersItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectOne(any())).thenReturn(item("item-1", OWNER, "sku-1", 5));
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(8);
		assertThrows(ArynBusinessException.class,
				() -> service.updateItem(TENANT, MEMBER, CART_ID, "item-1", dto));
	}

	@Test
	@DisplayName("非成员无法访问购物车详情")
	void nonMemberCannotReadCart() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectCount(any())).thenReturn(0L);
		assertThrows(ArynBusinessException.class, () -> service.getCartForUser(TENANT, "stranger", CART_ID));
	}

	@Test
	@DisplayName("已关闭的购物车不能再添加明细")
	void closedCartNotEditable() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_CLOSED));
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(1);
		assertThrows(ArynBusinessException.class, () -> service.addItem(TENANT, OWNER, CART_ID, dto));
	}

	@Test
	@DisplayName("确认人提交时按 SKU 聚合并生成整船订单")
	void confirmAggregatesAndCreatesOrder() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 5),
				item("item-2", MEMBER, "sku-1", 3), item("item-3", MEMBER, "sku-2", 2)));
		when(remoteShipProductProfileService.getSkuProfiles(eq(TENANT), anyList())).thenReturn(List.of());
		OrderInfo createdOrder = new OrderInfo();
		createdOrder.setId("order-1");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(createdOrder);

		String orderId = service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, new SharedCartConfirmDTO());
		assertEquals("order-1", orderId);

		ArgumentCaptor<CreateOrderDTO> captor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(captor.capture());
		CreateOrderDTO dto = captor.getValue();
		assertEquals("4", dto.getDeliveryWay());
		assertEquals("2", dto.getPurchaseScene());
		assertEquals("vessel-1", dto.getVesselId());
		assertEquals("SC" + CART_ID, dto.getRequestId());
		assertEquals(2, dto.getSkuReqList().size());
		assertEquals(8, dto.getSkuReqList().get(0).getQuantity());
		assertEquals(OWNER + "," + MEMBER, dto.getSkuReqList().get(0).getContributorUserId());
	}

	@Test
	@DisplayName("重复确认幂等返回原订单")
	void confirmIsIdempotent() {
		SharedCart submitted = cart(SharedCart.STATUS_SUBMITTED);
		submitted.setSubmitOrderId("order-1");
		when(cartMapper.selectOne(any())).thenReturn(submitted);

		String orderId = service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, new SharedCartConfirmDTO());
		assertEquals("order-1", orderId);
		verify(orderInfoService, never()).createOrder(any(CreateOrderDTO.class));
	}

	@Test
	@DisplayName("提交时数量未达 MOQ 被拒绝")
	void submitRejectsBelowMoq() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 7)));
		ShipSkuProfile profile = new ShipSkuProfile();
		profile.setSkuId("sku-1");
		profile.setMoq(10);
		profile.setStepQty(5);
		when(remoteShipProductProfileService.getSkuProfiles(eq(TENANT), anyList())).thenReturn(List.of(profile));

		assertThrows(ArynBusinessException.class,
				() -> service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, new SharedCartConfirmDTO()));
	}

	@Test
	@DisplayName("提交时数量不是步长整数倍被拒绝")
	void submitRejectsStepQtyViolation() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 7)));
		ShipSkuProfile profile = new ShipSkuProfile();
		profile.setSkuId("sku-1");
		profile.setMoq(5);
		profile.setStepQty(5);
		when(remoteShipProductProfileService.getSkuProfiles(eq(TENANT), anyList())).thenReturn(List.of(profile));

		assertThrows(ArynBusinessException.class,
				() -> service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, new SharedCartConfirmDTO()));
	}

	@Test
	@DisplayName("无确认权限的成员不能提交整船订单")
	void nonConfirmerCannotSubmit() {
		SharedCart collecting = cart(SharedCart.STATUS_COLLECTING);
		collecting.setConfirmerUserId("other-confirmer");
		when(cartMapper.selectOne(any())).thenReturn(collecting);
		when(memberMapper.selectCount(any())).thenReturn(0L);

		assertThrows(ArynBusinessException.class,
				() -> service.confirmAndCreateOrder(TENANT, MEMBER, CART_ID, new SharedCartConfirmDTO()));
	}

	@Test
	@DisplayName("核定数量调整后按核定值提交")
	void confirmAppliesApprovedQuantities() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 12)));
		when(remoteShipProductProfileService.getSkuProfiles(eq(TENANT), anyList())).thenReturn(List.of());
		OrderInfo createdOrder = new OrderInfo();
		createdOrder.setId("order-2");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(createdOrder);

		SharedCartConfirmDTO dto = new SharedCartConfirmDTO();
		SharedCartConfirmDTO.ApprovedQuantity change = new SharedCartConfirmDTO.ApprovedQuantity();
		change.setItemId("item-1");
		change.setQuantity(20);
		dto.setApprovedQuantities(List.of(change));

		service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, dto);

		ArgumentCaptor<CreateOrderDTO> captor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(captor.capture());
		assertEquals(20, captor.getValue().getSkuReqList().get(0).getQuantity());
	}

	@Test
	@DisplayName("发起人可以关闭未提交的购物车")
	void ownerClosesCart() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		service.close(TENANT, OWNER, CART_ID);
		ArgumentCaptor<SharedCart> captor = ArgumentCaptor.forClass(SharedCart.class);
		verify(cartMapper).updateById(captor.capture());
		assertEquals(SharedCart.STATUS_CLOSED, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("非发起人不能关闭购物车")
	void nonOwnerCannotClose() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		assertThrows(ArynBusinessException.class, () -> service.close(TENANT, MEMBER, CART_ID));
	}

}
