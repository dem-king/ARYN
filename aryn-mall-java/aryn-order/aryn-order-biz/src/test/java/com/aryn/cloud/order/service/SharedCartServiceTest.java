package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	private RemoteVesselService remoteVesselService;

	private RemoteMallUserService remoteMallUserService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		cartMapper = mock(SharedCartMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		itemMapper = mock(SharedCartItemMapper.class);
		orderInfoService = mock(IOrderInfoService.class);
		remoteShipProductProfileService = mock(RemoteShipProductProfileService.class);
		remoteVesselService = mock(RemoteVesselService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		// lambdaUpdate 需要实体表信息缓存，否则单测中抛
		// "MybatisPlus can not find lambda cache for this entity"
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCart.class);
		service = new SharedCartServiceImpl(cartMapper, memberMapper, itemMapper, orderInfoService);
		ReflectionTestUtils.setField(service, "remoteShipProductProfileService", remoteShipProductProfileService);
		ReflectionTestUtils.setField(service, "remoteVesselService", remoteVesselService);
		ReflectionTestUtils.setField(service, "remoteMallUserService", remoteMallUserService);
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
		when(cartMapper.selectOne(any())).thenReturn(null);
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-1");
		SharedCart created = service.create(TENANT, OWNER, dto);
		assertEquals(SharedCart.STATUS_COLLECTING, created.getStatus());
		assertEquals(OWNER, created.getOwnerUserId());
		verify(memberMapper).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("有效期由服务端计算为 24 小时，忽略客户端传值")
	void createComputesExpiryServerSide() {
		when(cartMapper.selectOne(any())).thenReturn(null);
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-1");
		// 客户端试图设置一个「不限期」之外的超长有效期，服务端必须忽略
		dto.setExpiresAt(LocalDateTime.now().plusDays(30));
		SharedCart created = service.create(TENANT, OWNER, dto);
		assertNotNull(created.getExpiresAt());
		assertTrue(created.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)),
				"有效期应约为 24 小时");
		assertTrue(created.getExpiresAt().isBefore(LocalDateTime.now().plusHours(25)),
				"有效期不应被客户端传值拉长");
	}

	@Test
	@DisplayName("同一船舶已有收集中购物车时复用该车并标记 adoptedExisting")
	void createReusesExistingCollectingCart() {
		SharedCart existing = cart(SharedCart.STATUS_COLLECTING);
		existing.setId("existing-cart");
		when(cartMapper.selectOne(any())).thenReturn(existing);

		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-1");
		SharedCart result = service.create(TENANT, OWNER, dto);

		assertEquals("existing-cart", result.getId());
		assertEquals(Boolean.TRUE, result.getAdoptedExisting());
		// 不应新建购物车，也不应重复插入发起人成员行
		verify(cartMapper, never()).insert(any(SharedCart.class));
		verify(memberMapper, never()).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("发起人可生成分享令牌，重复调用复用同一令牌")
	void ensureShareTokenIsStable() {
		SharedCart collecting = cart(SharedCart.STATUS_COLLECTING);
		collecting.setShareToken("token-1");
		when(cartMapper.selectOne(any())).thenReturn(collecting);

		assertEquals("token-1", service.ensureShareToken(TENANT, OWNER, CART_ID));
		// 已存在令牌时不应重复写库
		verify(cartMapper, never()).updateById(any(SharedCart.class));
	}

	@Test
	@DisplayName("非发起人不能生成分享令牌")
	void nonOwnerCannotShare() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		assertThrows(ArynBusinessException.class, () -> service.ensureShareToken(TENANT, MEMBER, CART_ID));
	}

	@Test
	@DisplayName("凭分享令牌加入：写入成员行并自动补建船舶成员关系")
	void joinByShareTokenAddsMemberAndBindsVessel() {
		SharedCart collecting = cart(SharedCart.STATUS_COLLECTING);
		collecting.setShareToken("token-1");
		when(cartMapper.selectOne(any())).thenReturn(collecting);
		when(memberMapper.selectCount(any())).thenReturn(0L);
		when(remoteVesselService.isVesselMember(TENANT, "vessel-1", "new-user")).thenReturn(false);

		SharedCart joined = service.joinByShareToken(TENANT, "new-user", "token-1");

		assertEquals(CART_ID, joined.getId());
		verify(remoteVesselService).bindMemberByShare(TENANT, "vessel-1", "new-user");
		verify(memberMapper).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("已是成员时凭令牌加入为幂等，不重复插入")
	void joinByShareTokenIsIdempotentForExistingMember() {
		SharedCart collecting = cart(SharedCart.STATUS_COLLECTING);
		collecting.setShareToken("token-1");
		when(cartMapper.selectOne(any())).thenReturn(collecting);
		when(memberMapper.selectCount(any())).thenReturn(1L);

		service.joinByShareToken(TENANT, OWNER, "token-1");

		verify(memberMapper, never()).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("令牌无效时拒绝加入")
	void joinByShareTokenRejectsUnknownToken() {
		when(cartMapper.selectOne(any())).thenReturn(null);
		assertThrows(ArynBusinessException.class,
				() -> service.joinByShareToken(TENANT, "new-user", "bad-token"));
	}

	@Test
	@DisplayName("已提交的购物车不接受新成员加入")
	void joinByShareTokenRejectsSubmittedCart() {
		SharedCart submitted = cart(SharedCart.STATUS_SUBMITTED);
		submitted.setShareToken("token-1");
		when(cartMapper.selectOne(any())).thenReturn(submitted);
		assertThrows(ArynBusinessException.class,
				() -> service.joinByShareToken(TENANT, "new-user", "token-1"));
	}

	@Test
	@DisplayName("订单签收后关联购物车归档为已完成")
	void archiveOnOrderSignedCompletesCart() {
		SharedCart submitted = cart(SharedCart.STATUS_SUBMITTED);
		submitted.setSubmitOrderId("order-1");
		when(cartMapper.selectOne(any())).thenReturn(submitted);
		when(cartMapper.update(any(), any())).thenReturn(1);

		assertTrue(service.archiveOnOrderSigned("order-1"));
	}

	@Test
	@DisplayName("无关订单不触发归档")
	void archiveOnOrderSignedIgnoresUnrelatedOrder() {
		when(cartMapper.selectOne(any())).thenReturn(null);
		assertTrue(!service.archiveOnOrderSigned("order-without-cart"));
		verify(cartMapper, never()).update(any(), any());
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
		// 按人拆行：3 条明细各自独立，同一 SKU 不再合并为一行
		assertEquals(3, dto.getSkuReqList().size());
		CreateOrderSkuReqDTO first = dto.getSkuReqList().stream()
			.filter(req -> OWNER.equals(req.getContributorUserId())).findFirst().orElseThrow();
		assertEquals("sku-1", first.getSkuId());
		assertEquals(5, first.getQuantity());
		CreateOrderSkuReqDTO second = dto.getSkuReqList().stream()
			.filter(req -> MEMBER.equals(req.getContributorUserId()) && "sku-1".equals(req.getSkuId()))
			.findFirst().orElseThrow();
		assertEquals(3, second.getQuantity());
		// 同一 SKU 的两位成员各自成行，而不是合并成一行 8 件
		long sameSkuRows = dto.getSkuReqList().stream().filter(req -> "sku-1".equals(req.getSkuId())).count();
		assertEquals(2, sameSkuRows);
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

	@Test
	@DisplayName("我的共享购物车列表补齐船舶上下文与查看者权限")
	void listMyCartsEnrichesContextAndPermissions() {
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_COLLECTING)));
		when(itemMapper.selectList(any())).thenReturn(List.of());
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());

		List<SharedCartVO> result = service.listMyCarts(TENANT, OWNER);

		assertEquals(1, result.size());
		SharedCartVO vo = result.get(0);
		assertEquals(CART_ID, vo.getId());
		assertEquals("悦航1号", vo.getVesselName());
		assertEquals("上海港", vo.getPortName());
		assertEquals("CNSHA", vo.getPortCode());
		assertEquals(SharedCartMember.ROLE_OWNER, vo.getViewerRole());
		assertTrue(vo.getViewerIsOwner());
		assertTrue(vo.getViewerCanEdit());
		assertTrue(vo.getViewerCanConfirm());
		assertEquals(1, vo.getMemberCount());
		assertEquals(0, vo.getItemCount());
	}

	@Test
	@DisplayName("普通成员可编辑但不可提交，权限标记由成员行决定")
	void listMyCartsMarksPlainMemberAsNonConfirmer() {
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "1", "0")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_COLLECTING)));
		when(itemMapper.selectList(any())).thenReturn(List.of());

		SharedCartVO vo = service.listMyCarts(TENANT, MEMBER).get(0);

		assertEquals(SharedCartMember.ROLE_MEMBER, vo.getViewerRole());
		assertEquals(Boolean.FALSE, vo.getViewerIsOwner());
		assertTrue(vo.getViewerCanEdit());
		assertEquals(Boolean.FALSE, vo.getViewerCanConfirm());
		// 船舶上下文未打桩，远程返回 null 时应降级为仅 ID，不得抛异常
		assertNull(vo.getVesselName());
	}

	@Test
	@DisplayName("未参与任何购物车时返回空列表，不查购物车主表")
	void listMyCartsReturnsEmptyWithoutMembership() {
		when(memberMapper.selectList(any())).thenReturn(List.of());

		assertTrue(service.listMyCarts(TENANT, MEMBER).isEmpty());
		verify(cartMapper, never()).selectList(any());
	}

	@Test
	@DisplayName("船舶域不可用时列表降级为仅返回ID，不影响可用性")
	void listMyCartsDegradesWhenVesselServiceFails() {
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_COLLECTING)));
		when(itemMapper.selectList(any())).thenReturn(List.of());
		when(remoteVesselService.getVesselCallContext(anyString(), anyString()))
			.thenThrow(new RuntimeException("vessel service down"));

		List<SharedCartVO> result = service.listMyCarts(TENANT, OWNER);

		assertEquals(1, result.size());
		assertEquals(CART_ID, result.get(0).getId());
		assertNull(result.get(0).getVesselName());
	}

	@Test
	@DisplayName("非成员访问共享购物车详情被拒绝")
	void getCartDetailRejectsNonMember() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectCount(any())).thenReturn(0L);

		assertThrows(ArynBusinessException.class, () -> service.getCartDetail(TENANT, MEMBER, CART_ID));
	}

	@Test
	@DisplayName("成员可读取详情并拿到查看者权限标记")
	void getCartDetailAllowsMember() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		// 非发起人需通过成员关系校验（requireMembership 走 selectCount）
		when(memberMapper.selectCount(any())).thenReturn(1L);
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, MEMBER, SharedCartMember.ROLE_CONFIRMATOR, "1", "1")));
		when(itemMapper.selectList(any())).thenReturn(List.of());
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());

		SharedCartVO vo = service.getCartDetail(TENANT, MEMBER, CART_ID);

		assertNotNull(vo);
		assertEquals(SharedCartMember.ROLE_CONFIRMATOR, vo.getViewerRole());
		assertTrue(vo.getViewerCanConfirm());
		assertEquals("悦航1号", vo.getVesselName());
	}

	private SharedCartMember member(String cartId, String userId, String role, String canEdit, String canConfirm) {
		SharedCartMember member = new SharedCartMember();
		member.setCartId(cartId);
		member.setUserId(userId);
		member.setMemberRole(role);
		member.setCanEdit(canEdit);
		member.setCanConfirm(canConfirm);
		member.setTenantId(TENANT);
		return member;
	}

	private VesselContextDTO vesselContext() {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId("vessel-1");
		context.setVesselName("悦航1号");
		context.setVesselCallId("call-1");
		context.setPortCode("CNSHA");
		context.setPortName("上海港");
		context.setBerth("洋山1号泊位");
		return context;
	}

}
