package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
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

	private SharedCartImportMapper importMapper;

	private SharedCartImportRowMapper importRowMapper;

	private IOrderInfoService orderInfoService;

	private RemoteVesselService remoteVesselService;

	private RemoteMallUserService remoteMallUserService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		cartMapper = mock(SharedCartMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		itemMapper = mock(SharedCartItemMapper.class);
		importMapper = mock(SharedCartImportMapper.class);
		importRowMapper = mock(SharedCartImportRowMapper.class);
		orderInfoService = mock(IOrderInfoService.class);
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
		service = new SharedCartServiceImpl(cartMapper, memberMapper, itemMapper, importMapper, importRowMapper,
				orderInfoService);
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

	/*
	 * 管理端成员权限开关（2026-10-09 补）。
	 *
	 * 开关本身只是写 can_edit，关键是它写下的值必须真的生效、且不能写出矛盾状态：
	 * 发起人天然可编辑（写了也是静默无效）、成员不存在要给明确报错。
	 */

	@Test
	@DisplayName("管理端收回成员编辑权：落库 can_edit=0，该成员随即不能再加购")
	void adminCanRevokeMemberEditPermission() {
		SharedCart cart = cart(SharedCart.STATUS_COLLECTING);
		when(cartMapper.selectOne(any())).thenReturn(cart);
		SharedCartMember member = member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "1", "0");
		member.setId("member-row-1");
		when(memberMapper.selectOne(any())).thenReturn(member);

		SharedCartMember updated = service.updateMemberCanEdit(TENANT, CART_ID, "member-row-1", "0");

		assertEquals("0", updated.getCanEdit());
		verify(memberMapper).updateById(member);

		// 写进去的值必须真的挡住加购：同一成员行重新读出后加购被拒
		when(memberMapper.selectOne(any())).thenReturn(updated);
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(1);
		assertThrows(ArynBusinessException.class, () -> service.addItem(TENANT, MEMBER, CART_ID, dto));
	}

	@Test
	@DisplayName("管理端恢复成员编辑权：落库 can_edit=1")
	void adminCanRestoreMemberEditPermission() {
		SharedCart cart = cart(SharedCart.STATUS_COLLECTING);
		when(cartMapper.selectOne(any())).thenReturn(cart);
		SharedCartMember member = member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "0", "0");
		member.setId("member-row-1");
		when(memberMapper.selectOne(any())).thenReturn(member);

		SharedCartMember updated = service.updateMemberCanEdit(TENANT, CART_ID, "member-row-1", "1");

		assertEquals("1", updated.getCanEdit());
		verify(memberMapper).updateById(member);
	}

	@Test
	@DisplayName("发起人不接受收回：他天然可编辑，写了也是静默无效，直接报错更诚实")
	void adminCannotRevokeOwnerEditPermission() {
		SharedCart cart = cart(SharedCart.STATUS_COLLECTING);
		when(cartMapper.selectOne(any())).thenReturn(cart);
		SharedCartMember ownerRow = member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1");
		ownerRow.setId("member-row-owner");
		when(memberMapper.selectOne(any())).thenReturn(ownerRow);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.updateMemberCanEdit(TENANT, CART_ID, "member-row-owner", "0"));
		assertTrue(exception.getMsg().contains("发起人"));
		verify(memberMapper, never()).updateById(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("成员不存在时给出明确报错，不静默成功")
	void adminCannotSetPermissionForUnknownMember() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectOne(any())).thenReturn(null);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.updateMemberCanEdit(TENANT, CART_ID, "ghost", "0"));
		assertTrue(exception.getMsg().contains("成员不存在"));
		verify(memberMapper, never()).updateById(any(SharedCartMember.class));
	}

	@Test
	@DisplayName("开关也接受已提交的车：归档后仍在改权限只能是无害操作，不因状态被拒")
	void adminCanSetPermissionOnSubmittedCart() {
		// 已提交车的成员明细不会被 requireCanEdit 用到（requireEditable 先拦下），
		// 所以这里刻意不加状态校验：加了只会让运营在历史单上看到无意义的报错。
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_SUBMITTED));
		SharedCartMember member = member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "1", "0");
		member.setId("member-row-1");
		when(memberMapper.selectOne(any())).thenReturn(member);

		assertEquals("0", service.updateMemberCanEdit(TENANT, CART_ID, "member-row-1", "0").getCanEdit());
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

	/*
	 * can_edit 约束力（2026-10-09 补）：
	 *
	 * 这四条守的是一个曾经**只在前端生效**的标记。此前服务端完全不看 can_edit，
	 * 于是「加购弹层里选不到共享车」与「直接调接口能写进去」同时成立，
	 * 演示数据把确认人写成 can_edit=0 时，用户看到的就是
	 * 「明明在车里，加购只能进个人购物车」。四条分别覆盖三个写入入口
	 * （加购/改数量/移除）与一个侧门（历史复用）。
	 */

	@Test
	@DisplayName("can_edit=0 的成员不能加购：只读是服务端约束，不是前端隐藏")
	void readOnlyMemberCannotAddItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectOne(any()))
			.thenReturn(member(CART_ID, MEMBER, SharedCartMember.ROLE_CONFIRMATOR, "0", "1"));
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(1);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.addItem(TENANT, MEMBER, CART_ID, dto));
		assertTrue(exception.getMsg().contains("权限"), "报错要说清是权限问题，而不是商品或库存");
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("can_edit=0 的成员不能改自己已加的行")
	void readOnlyMemberCannotUpdateOwnItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectOne(any())).thenReturn(item("item-1", MEMBER, "sku-1", 5));
		when(memberMapper.selectOne(any())).thenReturn(member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "0", "0"));
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(8);

		assertThrows(ArynBusinessException.class,
				() -> service.updateItem(TENANT, MEMBER, CART_ID, "item-1", dto));
		verify(itemMapper, never()).updateById(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("can_edit=0 的成员不能移除自己的行")
	void readOnlyMemberCannotRemoveOwnItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectOne(any())).thenReturn(item("item-1", MEMBER, "sku-1", 5));
		when(memberMapper.selectOne(any())).thenReturn(member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "0", "0"));

		assertThrows(ArynBusinessException.class,
				() -> service.removeItem(TENANT, MEMBER, CART_ID, "item-1"));
		verify(itemMapper, never()).updateById(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("can_edit=1 的成员照常加购：约束只挡只读成员，不误伤普通成员")
	void editableMemberCanAddItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectOne(any())).thenReturn(member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "1", "0"));
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(2);

		SharedCartItem added = service.addItem(TENANT, MEMBER, CART_ID, dto);

		assertEquals(MEMBER, added.getUserId(), "明细归属应是加购者自己");
		verify(itemMapper).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("发起人不查成员行即可加购：他创建车时就承担了维护职责")
	void ownerCanAddItemWithoutMemberRow() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		// 成员表里查不到发起人（历史数据/异常场景）也不该拦住他自己
		when(memberMapper.selectOne(any())).thenReturn(null);
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(1);

		SharedCartItem added = service.addItem(TENANT, OWNER, CART_ID, dto);

		assertEquals(OWNER, added.getUserId());
		verify(itemMapper).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("can_edit=0 的成员不能靠历史复用侧门给自己添行")
	void readOnlyMemberCannotReuseFromHistory() {
		SharedCart source = cart(SharedCart.STATUS_COMPLETED);
		SharedCart target = cart(SharedCart.STATUS_COLLECTING);
		target.setOwnerUserId("someone-else");
		when(cartMapper.selectOne(any())).thenReturn(source, target);
		// requireMembership(源单) -> 成员；目标车里已有该成员行且 can_edit=0
		when(memberMapper.selectCount(any())).thenReturn(1L);
		when(memberMapper.selectOne(any())).thenReturn(member(CART_ID, MEMBER, SharedCartMember.ROLE_MEMBER, "0", "0"));
		givenItemQueries(List.of(), List.of(item("a", MEMBER, "sku-1", 2)));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		assertThrows(ArynBusinessException.class,
				() -> service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto));
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("非成员加购报「无权访问」，不与只读区分开")
	void nonMemberCannotAddItem() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectOne(any())).thenReturn(null);
		SharedCartItemDTO dto = new SharedCartItemDTO();
		dto.setSkuId("sku-1");
		dto.setRequestedQuantity(1);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.addItem(TENANT, "stranger", CART_ID, dto));
		assertTrue(exception.getMsg().contains("无权访问"));
		verify(itemMapper, never()).insert(any(SharedCartItem.class));
	}

	@Test
	@DisplayName("确认人提交时按 SKU 聚合并生成整船订单")
	void confirmAggregatesAndCreatesOrder() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 5),
				item("item-2", MEMBER, "sku-1", 3), item("item-3", MEMBER, "sku-2", 2)));
		// 提交前会校验靠港可用性（ensureOrderableCall）：没有可用靠港会被拒绝整车提交
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());
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
	@DisplayName("确认提交透传支付类型：货到付款传 3，缺省为在线支付")
	void confirmPassesPaymentTypeThrough() {
		// 每次确认返回全新购物车：confirmAndCreateOrder 会把 cart 置为 SUBMITTED，
		// 同一实例第二次会命中幂等分支、不再建单
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING),
				cart(SharedCart.STATUS_COLLECTING));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("item-1", OWNER, "sku-1", 5)));
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());
		OrderInfo createdOrder = new OrderInfo();
		createdOrder.setId("order-1");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(createdOrder);

		SharedCartConfirmDTO codConfirm = new SharedCartConfirmDTO();
		codConfirm.setPaymentType("3");
		service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, codConfirm);
		ArgumentCaptor<CreateOrderDTO> codCaptor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(codCaptor.capture());
		assertEquals("3", codCaptor.getValue().getPaymentType());

		service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, new SharedCartConfirmDTO());
		ArgumentCaptor<CreateOrderDTO> onlineCaptor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService, times(2)).createOrder(onlineCaptor.capture());
		assertNull(onlineCaptor.getAllValues().get(1).getPaymentType());
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
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());
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
		when(remoteVesselService.getVesselCallSnapshot(TENANT, "call-1")).thenReturn(vesselContext());

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
	@DisplayName("已结束靠港的历史单照样返回船名与港口：靠港离港不等于查不到名字")
	void listMyCartsKeepsVesselNameForFinishedCall() {
		// 历史单（已提交/已完成）绑定的靠港必然已经结束。若用「可下单」查询补展示字段，
		// 这些卡片会永久退化成「船舶信息加载中」——正是本次要修的缺陷。
		// 展示字段必须走不受可下单过滤的快照查询。
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_SUBMITTED)));
		when(itemMapper.selectList(any())).thenReturn(List.of());
		when(remoteVesselService.getVesselCallSnapshot(TENANT, "call-1")).thenReturn(staleVesselContext());

		SharedCartVO vo = service.listMyCarts(TENANT, OWNER).get(0);

		assertEquals("悦航2号", vo.getVesselName());
		assertEquals("福建港", vo.getPortName());
		assertEquals("321", vo.getBerth());
		// 靠港是否仍可下单要单独下发：展示字段有值不代表还能下单（详情页据此提示顺延）
		assertEquals(Boolean.FALSE, vo.getCallOrderable());
	}

	@Test
	@DisplayName("同一靠港的多个购物车只查一次快照")
	void listMyCartsDeduplicatesSnapshotLookups() {
		// 同一航次的多次采购挂在同一个靠港上，列表一次取数不该按车重复查远程
		SharedCart first = cart(SharedCart.STATUS_SUBMITTED);
		SharedCart second = cart(SharedCart.STATUS_COMPLETED);
		second.setId("cart-2");
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1"),
					member("cart-2", OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(first, second));
		when(itemMapper.selectList(any())).thenReturn(List.of());
		when(remoteVesselService.getVesselCallSnapshot(TENANT, "call-1")).thenReturn(vesselContext());

		List<SharedCartVO> result = service.listMyCarts(TENANT, OWNER);

		assertEquals(2, result.size());
		assertEquals("悦航1号", result.get(0).getVesselName());
		assertEquals("悦航1号", result.get(1).getVesselName());
		verify(remoteVesselService, times(1)).getVesselCallSnapshot(TENANT, "call-1");
	}

	@Test
	@DisplayName("已提交的单一并统计核定行：不能再显示「0 项商品」")
	void listMyCartsCountsConfirmedRows() {
		// 提交时明细行被置为 ITEM_CONFIRMED，列表若只数 ITEM_PENDING 就会让
		// 已提交/已完成的单显示 0 项（列表页截图里的真实缺陷）。
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_SUBMITTED)));
		SharedCartItem confirmed = item("item-1", OWNER, "sku-1", 5);
		confirmed.setStatus(SharedCartItem.ITEM_CONFIRMED);
		SharedCartItem pending = item("item-2", OWNER, "sku-2", 3);
		when(itemMapper.selectList(any())).thenReturn(List.of(confirmed, pending));

		SharedCartVO vo = service.listMyCarts(TENANT, OWNER).get(0);

		assertEquals(2, vo.getItemCount());
	}

	@Test
	@DisplayName("明细按「未移除」统计，而不是按「待确认」统计")
	void listMyCartsQueryExcludesOnlyRemovedRows() {
		// 排除条件必须写在 SQL 里（木桩返回的行不会经过 where），因此这里断言查询形状：
		// 条件从 eq(ITEM_PENDING) 变回 eq(ITEM_PENDING) 就等于让已提交单重新显示 0 项。
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_COLLECTING)));
		when(itemMapper.selectList(any())).thenReturn(List.of());

		service.listMyCarts(TENANT, OWNER);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<SharedCartItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(itemMapper).selectList(captor.capture());
		String segment = captor.getValue().getSqlSegment();
		// "<>" 即 ne(status, ITEM_REMOVED)：只排除已移除的行
		assertTrue(segment.contains("<>"), "明细查询应排除已移除行：" + segment);
	}

	@Test
	@DisplayName("列表卡片按有效明细计数，已移除的不计入")
	void listMyCartsCountsActiveItems() {
		when(memberMapper.selectList(any()))
			.thenReturn(List.of(member(CART_ID, OWNER, SharedCartMember.ROLE_OWNER, "1", "1")));
		when(cartMapper.selectList(any())).thenReturn(List.of(cart(SharedCart.STATUS_COLLECTING)));
		SharedCartItem first = item("item-1", OWNER, "sku-1", 5);
		SharedCartItem second = item("item-2", OWNER, "sku-2", 3);
		when(itemMapper.selectList(any())).thenReturn(List.of(first, second));

		SharedCartVO vo = service.listMyCarts(TENANT, OWNER).get(0);

		assertEquals(2, vo.getItemCount());
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
		when(remoteVesselService.getVesselCallSnapshot(anyString(), anyString()))
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
		when(remoteVesselService.getVesselCallSnapshot(TENANT, "call-1")).thenReturn(vesselContext());

		SharedCartVO vo = service.getCartDetail(TENANT, MEMBER, CART_ID);

		assertNotNull(vo);
		assertEquals(SharedCartMember.ROLE_CONFIRMATOR, vo.getViewerRole());
		assertTrue(vo.getViewerCanConfirm());
		assertEquals("悦航1号", vo.getVesselName());
	}

	@Test
	@DisplayName("核定 0 = 本次不采：该行不生成订单明细")
	void confirmSkipsRowsApprovedAsZero() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectCount(any())).thenReturn(1L);
		SharedCartItem buy = item("item-1", MEMBER, "sku-1", 2);
		SharedCartItem skip = item("item-2", MEMBER, "sku-2", 3);
		givenItemQueries(List.of(buy, skip), List.of(buy, skip));
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());
		OrderInfo createdOrder = new OrderInfo();
		createdOrder.setId("order-1");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(createdOrder);

		SharedCartConfirmDTO dto = new SharedCartConfirmDTO();
		SharedCartConfirmDTO.ApprovedQuantity buyQty = new SharedCartConfirmDTO.ApprovedQuantity();
		buyQty.setItemId("item-1");
		buyQty.setQuantity(2);
		SharedCartConfirmDTO.ApprovedQuantity skipQty = new SharedCartConfirmDTO.ApprovedQuantity();
		skipQty.setItemId("item-2");
		skipQty.setQuantity(0);
		dto.setApprovedQuantities(List.of(buyQty, skipQty));

		service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, dto);

		ArgumentCaptor<CreateOrderDTO> captor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(captor.capture());
		List<CreateOrderSkuReqDTO> skuReqs = captor.getValue().getSkuReqList();
		assertEquals(1, skuReqs.size());
		assertEquals("sku-1", skuReqs.get(0).getSkuId());
		// 被排除的那行仍落库核定结果，成员端能看到「报的这项没买」
		assertEquals(0, skip.getApprovedQuantity());
		assertEquals(SharedCartItem.ITEM_CONFIRMED, skip.getStatus());
	}

	@Test
	@DisplayName("核定 0：全部行都排除时给出可读报错，不落到下单去抛底层异常")
	void confirmRejectsWhenEveryRowExcluded() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectCount(any())).thenReturn(1L);
		SharedCartItem only = item("item-1", MEMBER, "sku-1", 2);
		givenItemQueries(List.of(only), List.of(only));
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());

		SharedCartConfirmDTO dto = new SharedCartConfirmDTO();
		SharedCartConfirmDTO.ApprovedQuantity zero = new SharedCartConfirmDTO.ApprovedQuantity();
		zero.setItemId("item-1");
		zero.setQuantity(0);
		dto.setApprovedQuantities(List.of(zero));

		ArynBusinessException ex = assertThrows(ArynBusinessException.class,
				() -> service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, dto));
		assertTrue(ex.getMsg().contains("本次不采"), "报错要说清原因，实际：" + ex.getMsg());
		verify(orderInfoService, never()).createOrder(any());
	}

	@Test
	@DisplayName("核定数量不能为负数")
	void confirmRejectsNegativeApprovedQuantity() {
		when(cartMapper.selectOne(any())).thenReturn(cart(SharedCart.STATUS_COLLECTING));
		when(memberMapper.selectCount(any())).thenReturn(1L);
		SharedCartItem buy = item("item-1", MEMBER, "sku-1", 2);
		givenItemQueries(List.of(buy), List.of(buy));
		when(remoteVesselService.getVesselCallContext(TENANT, "call-1")).thenReturn(vesselContext());

		SharedCartConfirmDTO dto = new SharedCartConfirmDTO();
		SharedCartConfirmDTO.ApprovedQuantity negative = new SharedCartConfirmDTO.ApprovedQuantity();
		negative.setItemId("item-1");
		negative.setQuantity(-1);
		dto.setApprovedQuantities(List.of(negative));

		assertThrows(ArynBusinessException.class,
				() -> service.confirmAndCreateOrder(TENANT, OWNER, CART_ID, dto));
		verify(orderInfoService, never()).createOrder(any());
	}

	@Test
	@DisplayName("历史复用：核定数量优先，同一 SKU 多行合并为一行")
	void reuseFromHistoryMergesBySkuAndPrefersApprovedQuantity() {
		// 源单：已完成的历史单，同一 SKU 因「按人拆行」出现两行
		SharedCart source = cart(SharedCart.STATUS_COMPLETED);
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem rowA = item("a", MEMBER, "sku-1", 5);
		rowA.setApprovedQuantity(2);
		SharedCartItem rowB = item("b", OWNER, "sku-1", 3);
		rowB.setApprovedQuantity(1);
		// 第二行没有核定数量，应回落申请量
		SharedCartItem rowC = item("c", OWNER, "sku-2", 4);
		givenItemQueries(List.of(), List.of(rowA, rowB, rowC));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		// sku-1: 核定 2+1=3；sku-2: 回落申请量 4
		assertEquals(2, result.getReusedCount());
		assertEquals(0, result.getSkippedCount());
		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper, times(2)).insert(captor.capture());
		List<SharedCartItem> inserted = captor.getAllValues();
		SharedCartItem first = inserted.stream().filter(i -> "sku-1".equals(i.getSkuId())).findFirst().orElseThrow();
		assertEquals(3, first.getRequestedQuantity());
		SharedCartItem second = inserted.stream().filter(i -> "sku-2".equals(i.getSkuId())).findFirst().orElseThrow();
		assertEquals(4, second.getRequestedQuantity());
	}

	@Test
	@DisplayName("历史复用：上一轮核定为「本次不采」（0）的行不搬过来")
	void reuseFromHistorySkipsRowsApprovedAsZero() {
		// 核定为 0 是「那次没买」，属于既成事实；搬过来会凭空多出一项没人要的商品
		SharedCart source = cart(SharedCart.STATUS_SUBMITTED);
		when(cartMapper.selectOne(any())).thenReturn(source, null);
		when(memberMapper.selectCount(any())).thenReturn(1L, 0L);
		SharedCartItem skipped = item("a", MEMBER, "sku-1", 5);
		skipped.setApprovedQuantity(0);
		SharedCartItem kept = item("b", MEMBER, "sku-2", 3);
		kept.setApprovedQuantity(2);
		givenItemQueries(List.of(), List.of(skipped, kept));

		SharedCartReuseDTO dto = new SharedCartReuseDTO();
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-new");

		SharedCartReuseVO result = service.reuseFromHistory(TENANT, MEMBER, CART_ID, dto);

		assertEquals(1, result.getReusedCount());
		assertEquals(1, result.getSkippedCount());
		ArgumentCaptor<SharedCartItem> captor = ArgumentCaptor.forClass(SharedCartItem.class);
		verify(itemMapper).insert(captor.capture());
		assertEquals("sku-2", captor.getValue().getSkuId());
		assertEquals(2, captor.getValue().getRequestedQuantity());
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
		context.setCallOrderable(Boolean.TRUE);
		return context;
	}

	/** 已结束（离港）的靠港：展示字段齐全，但不可再下单 */
	private VesselContextDTO staleVesselContext() {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId("vessel-1");
		context.setVesselName("悦航2号");
		context.setVesselCallId("call-1");
		context.setPortCode("CNFZH");
		context.setPortName("福建港");
		context.setBerth("321");
		context.setCallOrderable(Boolean.FALSE);
		return context;
	}

}
