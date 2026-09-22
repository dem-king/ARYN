package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartPlanDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import static org.mockito.Mockito.times;
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
		// requireConfirmer / requireMembership 都走 lambdaQuery；不初始化成员表信息
		// 单测里连 SQL 片段都取不到，只能靠 any() 蒙混，无法区分两种权限
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCartMember.class);
		// 明细同理：listItems（status =）与 listReusableItems（status IN）都走 selectList，
		// 不初始化就只能用 any()，无法区分两者——而那正是"复用漏掉已确认行"的缺陷点
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCartItem.class);
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
		givenItemQueries(List.of(), List.of());
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

	@Test
	@DisplayName("排计划：确认人可给任意成员的明细行排计划")
	void updateItemPlanAllowsConfirmer() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		SharedCartItem target = item("item-1", MEMBER, "sku-1", 6);
		when(itemMapper.selectOne(any())).thenReturn(target);

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		dto.setPlannedQuantity(4);
		dto.setFulfilledQuantity(1);

		SharedCartItem updated = service.updateItemPlan(TENANT, OWNER, CART_ID, dto);

		assertEquals(4, updated.getPlannedQuantity());
		assertEquals(1, updated.getFulfilledQuantity());
		verify(itemMapper).updateById(target);
	}

	@Test
	@DisplayName("排计划：普通成员是有效成员但无确认权时仍无权排计划")
	void updateItemPlanRejectsPlainMember() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectOne(any())).thenReturn(item("item-1", MEMBER, "sku-1", 6));
		// 该成员是有效成员（成员关系查得到），但成员行的 canConfirm=0：
		// 这两个查询都走 selectCount，靠 SQL 里是否带 can_confirm 区分，
		// 否则用 any() 一把梭会让「权限收紧」和「权限放宽」得到同样结果。
		when(memberMapper.selectCount(any())).thenAnswer(invocation -> {
			Wrapper<SharedCartMember> wrapper = invocation.getArgument(0);
			return wrapper.getSqlSegment().contains("can_confirm") ? 0L : 1L;
		});

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		dto.setPlannedQuantity(4);

		assertThrows(ArynBusinessException.class,
				() -> service.updateItemPlan(TENANT, MEMBER, CART_ID, dto));
		verify(itemMapper, never()).updateById(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("排计划：clearPlanned=true 才清空计划量")
	void updateItemPlanClearsOnlyWhenExplicit() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		SharedCartItem target = item("item-1", MEMBER, "sku-1", 6);
		target.setPlannedQuantity(4);
		target.setFulfilledQuantity(2);
		when(itemMapper.selectOne(any())).thenReturn(target);

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		// plannedQuantity 为空但未显式 clearPlanned：应保持原计划不动
		dto.setFulfilledQuantity(3);

		SharedCartItem updated = service.updateItemPlan(TENANT, OWNER, CART_ID, dto);

		assertEquals(4, updated.getPlannedQuantity());
		assertEquals(3, updated.getFulfilledQuantity());
	}

	@Test
	@DisplayName("排计划：只改已采量不会抹掉计划（clearPlanned 缺席时的语义）")
	void updateItemPlanOnlyFulfilledKeepsPlan() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		SharedCartItem target = item("item-1", MEMBER, "sku-1", 6);
		target.setPlannedQuantity(4);
		when(itemMapper.selectOne(any())).thenReturn(target);

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		dto.setPlannedQuantity(4);
		dto.setFulfilledQuantity(4);

		SharedCartItem updated = service.updateItemPlan(TENANT, OWNER, CART_ID, dto);

		assertEquals(4, updated.getPlannedQuantity());
		assertEquals(4, updated.getFulfilledQuantity());
	}

	@Test
	@DisplayName("排计划：显式 clearPlanned=true 清空计划，行退出进度统计")
	void updateItemPlanClearsPlanWhenRequested() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		SharedCartItem target = item("item-1", MEMBER, "sku-1", 6);
		target.setPlannedQuantity(4);
		target.setFulfilledQuantity(2);
		when(itemMapper.selectOne(any())).thenReturn(target);

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		dto.setClearPlanned(true);

		SharedCartItem updated = service.updateItemPlan(TENANT, OWNER, CART_ID, dto);

		assertNull(updated.getPlannedQuantity());
		// 清计划不该顺手把已采量清零（已采是既成事实）
		assertEquals(2, updated.getFulfilledQuantity());
	}

	@Test
	@DisplayName("排计划：存量行的已采量为 null 时补 0，保证进度口径统一")
	void updateItemPlanBackfillsNullFulfilled() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		SharedCartItem target = item("item-1", MEMBER, "sku-1", 6);
		target.setPlannedQuantity(4);
		target.setFulfilledQuantity(null);
		when(itemMapper.selectOne(any())).thenReturn(target);

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");

		SharedCartItem updated = service.updateItemPlan(TENANT, OWNER, CART_ID, dto);

		assertEquals(0, updated.getFulfilledQuantity());
	}

	@Test
	@DisplayName("排计划：已提交的购物车不能再改计划")
	void updateItemPlanRejectsReadonlyCart() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_SUBMITTED));
		when(itemMapper.selectOne(any())).thenReturn(item("item-1", MEMBER, "sku-1", 6));

		SharedCartPlanDTO dto = new SharedCartPlanDTO();
		dto.setItemId("item-1");
		dto.setPlannedQuantity(4);

		assertThrows(ArynBusinessException.class,
				() -> service.updateItemPlan(TENANT, OWNER, CART_ID, dto));
	}

	@Test
	@DisplayName("历史复用：计划量优先，同一 SKU 多行合并为一行")
	void reuseFromHistoryMergesBySkuAndPrefersPlannedQuantity() {
		// 源单：已完成的历史单，同一 SKU 因「按人拆行」出现两行
		SharedCart source = cart(SharedCart.STATUS_COMPLETED);
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem rowA = item("a", MEMBER, "sku-1", 5);
		rowA.setPlannedQuantity(2);
		rowA.setFulfilledQuantity(2);
		SharedCartItem rowB = item("b", OWNER, "sku-1", 3);
		rowB.setPlannedQuantity(1);
		rowB.setFulfilledQuantity(0);
		// 第二行没有计划量，应回落申请量
		SharedCartItem rowC = item("c", OWNER, "sku-2", 4);
		givenItemQueries(List.of(), List.of(rowA, rowB, rowC));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		// sku-1: 计划量 2+1=3；sku-2: 回落申请量 4
		assertEquals(2, result.getReusedCount());
		assertEquals(0, result.getSkippedCount());
		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper, times(2)).insert(captor.capture());
		List<SharedCartItem> inserted = captor.getAllValues();
		SharedCartItem first = inserted.stream().filter(i -> "sku-1".equals(i.getSkuId())).findFirst().orElseThrow();
		assertEquals(3, first.getRequestedQuantity());
		assertEquals(3, first.getPlannedQuantity());
		SharedCartItem second = inserted.stream().filter(i -> "sku-2".equals(i.getSkuId())).findFirst().orElseThrow();
		assertEquals(4, second.getRequestedQuantity());
		// 源单没排计划 -> 目标行也不许拿申请量充数
		assertNull(second.getPlannedQuantity());
	}

	@Test
	@DisplayName("历史复用：不继承已采量（否则新一轮进度一上来就是满的）")
	void reuseFromHistoryDoesNotCarryFulfilledQuantity() {
		SharedCart source = cart(SharedCart.STATUS_SUBMITTED);
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem row = item("a", MEMBER, "sku-1", 5);
		row.setPlannedQuantity(5);
		row.setFulfilledQuantity(5);
		givenItemQueries(List.of(), List.of(row));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals(0, captor.getValue().getFulfilledQuantity());
		assertEquals(SharedCartItem.ITEM_PENDING, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("历史复用：已提交单里被核定过的行（ITEM_CONFIRMED）也要搬，不能漏项")
	void reuseFromHistoryIncludesConfirmedRows() {
		// 这是 listItems 的坑：它只取 ITEM_PENDING，已提交单里核定过的行是 ITEM_CONFIRMED。
		// 若复用直接调用 listItems，复用出来的清单会凭空少几项。
		SharedCart source = cart(SharedCart.STATUS_SUBMITTED);
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem confirmed = item("a", MEMBER, "sku-1", 5);
		confirmed.setStatus(SharedCartItem.ITEM_CONFIRMED);
		givenItemQueries(List.of(), List.of(confirmed));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		assertEquals(1, result.getReusedCount());
	}

	@Test
	@DisplayName("历史复用：目标车已有该 SKU 则跳过，重复点击不会翻倍")
	void reuseFromHistorySkipsSkuAlreadyInTarget() {
		SharedCart source = cart(SharedCart.STATUS_COMPLETED);
		SharedCart target = cart(SharedCart.STATUS_COLLECTING);
		when(cartMapper.selectOne(any())).thenReturn(source, target);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem sourceRow = item("a", MEMBER, "sku-1", 5);
		SharedCartItem existingRow = item("b", MEMBER, "sku-1", 9);
		// 目标车已有 sku-1，源单也含 sku-1 -> 应跳过
		givenItemQueries(List.of(existingRow), List.of(sourceRow));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		assertEquals(0, result.getReusedCount());
		assertEquals(1, result.getSkippedCount());
		assertEquals("本次清单中已有该商品", result.getSkipped().get(0).getReason());
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("历史复用：非源单成员被拒（否则可借复用读取他人清单）")
	void reuseFromHistoryRejectsNonMember() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COMPLETED));
		when(memberMapper.selectCount(any())).thenReturn(0L);

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		assertThrows(ArynBusinessException.class,
				() -> service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto));
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("历史复用：不能跨船复用")
	void reuseFromHistoryRejectsDifferentVessel() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COMPLETED));
		when(memberMapper.selectCount(any())).thenReturn(1L);

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-other");
		dto.setVesselCallId("call-new");

		assertThrows(ArynBusinessException.class,
				() -> service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto));
	}

	@Test
	@DisplayName("历史复用：进行中的单不能复用（它本身就是本轮清单）")
	void reuseFromHistoryRejectsActiveCart() {
		for (String status : List.of(SharedCart.STATUS_DRAFT, SharedCart.STATUS_COLLECTING,
				SharedCart.STATUS_WAITING_CONFIRM)) {
			when(cartMapper.selectOne(any())).thenReturn(cart(status));
			when(memberMapper.selectCount(any())).thenReturn(1L);

			SharedCartReuseDTO dto = new SharedCartReuseDTO();
			dto.setVesselId("vessel-1");
			dto.setVesselCallId("call-new");

			assertThrows(ArynBusinessException.class,
					() -> service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto), status);
		}
	}

	@Test
	@DisplayName("历史复用：并入侵已有进行中车时，复用者补成员关系（否则写进一张自己打不开的清单）")
	void reuseFromHistoryBindsMemberWhenAdoptingExistingCart() {
		SharedCart source = cart(SharedCart.STATUS_COMPLETED);
		// create() 命中同船已有收集中购物车 -> 直接回传该车，不补成员行
		SharedCart target = cart(SharedCart.STATUS_COLLECTING);
		target.setOwnerUserId("someone-else");
		when(cartMapper.selectOne(any())).thenReturn(source, target);
		// requireMembership(源) -> 1；ensureReuseMembership 查目标 -> 0；requireConfirmer 不用
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem sourceRow = item("a", MEMBER, "sku-1", 2);
		givenItemQueries(List.of(), List.of(sourceRow));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		assertEquals(Boolean.TRUE, result.getAdoptedExisting());
		verify(memberMapper).insert(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("历史复用：备注不传时沿用源单备注")
	void reuseFromHistoryFallsBackToSourceRemark() {
		SharedCart source = cart(SharedCart.STATUS_CLOSED);
		source.setRemark("上航次备注");
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		when(itemMapper.selectList(any())).thenReturn(List.of());

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		ArgumentCaptor<SharedCart> captor = ArgumentCaptor.forClass(SharedCart.class);
		verify(cartMapper).insert(captor.capture());
		assertEquals("上航次备注", captor.getValue().getRemark());
	}

	/**
	 * 按 SQL 片段分派明细查询结果。
	 *
	 * <p>`listItems`（目标车去重，`status =`）与 `listReusableItems`（源单，`status IN`）
	 * 都走 `itemMapper.selectList`。若一律用 `any()`，把复用改成 `listItems` 也不会
	 * 有测试失败——而"漏掉已提交单里核定过的行"正是本功能的真实缺陷点。
	 */
	private void givenItemQueries(List<SharedCartItem> targetExisting, List<SharedCartItem> sourceRows) {
		when(itemMapper.selectList(any())).thenAnswer(invocation -> {
			Wrapper<SharedCartItem> wrapper = invocation.getArgument(0);
			return wrapper.getSqlSegment().contains("status IN") ? sourceRows : targetExisting;
		});
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
