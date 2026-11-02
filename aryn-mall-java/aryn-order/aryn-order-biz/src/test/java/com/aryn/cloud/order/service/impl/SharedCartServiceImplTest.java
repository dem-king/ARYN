package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.vo.SharedCartMemberCandidateVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.service.IDeliveryAreaService;
import com.aryn.cloud.order.service.SharedCartInviteNotifyService;
import com.aryn.cloud.order.support.ChainAiAssistant;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享购物车提交链路单测：船供车的靠港顺延 + 岸上企业车的地址链路。
 *
 * <p>背景（2026-10-04）：整车的 vessel_call_id 是创建时刻的快照，而收集中会持续数天，
 * 靠港 ETD 过点后提交会被下单校验以「靠港计划不可用（不存在或已离港）」拒绝，且共享车
 * 没有切换靠港的出路，全员明细被扣死；成员端查找活动车有回退、加购照常，问题直到
 * 提交才暴露，更加隐蔽。提交时顺延到该船当前可用靠港（ETA 最早一班），无可用靠港时
 * 给可读报错引导先申报。
 *
 * <p>2026-10-24 新增岸上企业车（cartType=2）：港口附近工厂等无船客户按收货地址发起，
 * 提交生成企业采购订单（way=3 / scene=3），地址被删或范围收窄时给出可读报错。
 */
class SharedCartServiceImplTest {

	private static final String TENANT = "tenant-1";

	private SharedCartMapper sharedCartMapper;
	private SharedCartItemMapper sharedCartItemMapper;
	private SharedCartMemberMapper sharedCartMemberMapper;
	private IOrderInfoService orderInfoService;
	private RemoteVesselService remoteVesselService;
	private RemoteMallUserService remoteMallUserService;
	private RemoteUserAddressService remoteUserAddressService;
	private IDeliveryAreaService deliveryAreaService;
	private SharedCartInviteNotifyService inviteNotifyService;
	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		sharedCartMapper = mock(SharedCartMapper.class);
		sharedCartItemMapper = mock(SharedCartItemMapper.class);
		sharedCartMemberMapper = mock(SharedCartMemberMapper.class);
		orderInfoService = mock(IOrderInfoService.class);
		remoteVesselService = mock(RemoteVesselService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		remoteUserAddressService = mock(RemoteUserAddressService.class);
		deliveryAreaService = mock(IDeliveryAreaService.class);
		inviteNotifyService = mock(SharedCartInviteNotifyService.class);
		service = new SharedCartServiceImpl(sharedCartMapper, sharedCartMemberMapper,
				sharedCartItemMapper, mock(SharedCartImportMapper.class),
				mock(SharedCartImportRowMapper.class), orderInfoService, deliveryAreaService,
				mock(ChainAiAssistant.class), inviteNotifyService);
		// @DubboReference 字段不走构造器，测试里反射注入
		ReflectionTestUtils.setField(service, "remoteVesselService", remoteVesselService);
		ReflectionTestUtils.setField(service, "remoteMallUserService", remoteMallUserService);
		ReflectionTestUtils.setField(service, "remoteUserAddressService", remoteUserAddressService);
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

	// ------------------------------------------------------- 岸上企业车（cartType=2）

	@Test
	void createShoreCartRequiresOwnerAddressInDeliveryArea() {
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setCartType(SharedCart.CART_TYPE_SHORE);
		// 缺地址直接拒绝，不落到去重查询
		assertThatThrownBy(() -> service.create(TENANT, "user-1", dto))
			.isInstanceOfSatisfying(ArynBusinessException.class, ex -> assertThat(ex.getMsg()).contains("收货地址"));

		dto.setUserAddressId("addr-1");
		// 地址簿查不到（不存在或不属于发起人）
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(null);
		assertThatThrownBy(() -> service.create(TENANT, "user-1", dto))
			.isInstanceOfSatisfying(ArynBusinessException.class, ex -> assertThat(ex.getMsg()).contains("不属于你"));

		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(shoreAddress());
		when(deliveryAreaService.isAddressInDeliveryArea("320000", "320600", "320602")).thenReturn(false);
		assertThatThrownBy(() -> service.create(TENANT, "user-1", dto))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				ex -> assertThat(ex.getMsg()).contains("不在公司配送"));
	}

	@Test
	void createShoreCartSkipsVesselChecksAndWritesAddressSnapshot() {
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setCartType(SharedCart.CART_TYPE_SHORE);
		dto.setUserAddressId("addr-1");
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(shoreAddress());
		when(deliveryAreaService.isAddressInDeliveryArea("320000", "320600", "320602")).thenReturn(true);
		// 无已有收集中车
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(null);

		SharedCart created = service.create(TENANT, "user-1", dto);

		ArgumentCaptor<SharedCart> cartCaptor = ArgumentCaptor.forClass(SharedCart.class);
		verify(sharedCartMapper).insert(cartCaptor.capture());
		assertThat(cartCaptor.getValue().getCartType()).isEqualTo(SharedCart.CART_TYPE_SHORE);
		assertThat(cartCaptor.getValue().getVesselId()).isNull();
		assertThat(cartCaptor.getValue().getUserAddressId()).isEqualTo("addr-1");
		assertThat(cartCaptor.getValue().getAddressSnapshot()).contains("张三").contains("13800000000");
		// 岸上车不校验船舶成员
		verify(remoteVesselService, never()).getVesselMemberRole(any(), any(), any());
		assertThat(created.getCartType()).isEqualTo(SharedCart.CART_TYPE_SHORE);
	}

	@Test
	void createShoreCartAdoptsOwnActiveCart() {
		SharedCartCreateDTO dto = new SharedCartCreateDTO();
		dto.setCartType(SharedCart.CART_TYPE_SHORE);
		dto.setUserAddressId("addr-1");
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(shoreAddress());
		when(deliveryAreaService.isAddressInDeliveryArea("320000", "320600", "320602")).thenReturn(true);
		SharedCart active = shoreCollectingCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(active);
		// 发起人已在成员表（owner 建车时写入），adopt 走幂等分支
		when(sharedCartMemberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(sharedCartMemberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

		SharedCart result = service.create(TENANT, "user-1", dto);

		assertThat(result.getAdoptedExisting()).isTrue();
		verify(sharedCartMapper, never()).insert(any(SharedCart.class));
	}

	@Test
	void submitShoreCartBuildsEnterpriseOrderWithAddress() {
		SharedCart cart = shoreCollectingCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(shoreAddress());
		when(deliveryAreaService.isAddressInDeliveryArea("320000", "320600", "320602")).thenReturn(true);
		when(sharedCartItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item()));
		OrderInfo order = new OrderInfo();
		order.setId("order-9");
		when(orderInfoService.createOrder(any(CreateOrderDTO.class))).thenReturn(order);

		String orderId = service.confirmAndCreateOrder(TENANT, "user-1", "cart-1", null);

		assertThat(orderId).isEqualTo("order-9");
		ArgumentCaptor<CreateOrderDTO> dtoCaptor = ArgumentCaptor.forClass(CreateOrderDTO.class);
		verify(orderInfoService).createOrder(dtoCaptor.capture());
		CreateOrderDTO created = dtoCaptor.getValue();
		assertThat(created.getDeliveryWay()).isEqualTo("3");
		assertThat(created.getPurchaseScene()).isEqualTo("3");
		assertThat(created.getUserAddressId()).isEqualTo("addr-1");
		assertThat(created.getVesselId()).isNull();
		assertThat(created.getVesselCallId()).isNull();
		// 岸上车不碰船舶域：既不顺延靠港也不校验船舶上下文
		verify(remoteVesselService, never()).getVesselCallContext(any(), any());
		verify(remoteVesselService, never()).resolveAvailableCall(any(), any());
	}

	@Test
	void submitShoreCartFailsWithReadableErrorWhenAddressDeleted() {
		SharedCart cart = shoreCollectingCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteUserAddressService.getById("addr-1", "user-1")).thenReturn(null);

		assertThatThrownBy(() -> service.confirmAndCreateOrder(TENANT, "user-1", "cart-1", null))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				ex -> assertThat(ex.getMsg()).contains("已被删除"));
		verify(orderInfoService, never()).createOrder(any(CreateOrderDTO.class));
	}

	// ------------------------------------------------------- 邀请成员：校验 + 通知 + 候选

	@Test
	void inviteRejectsUnknownUserInsteadOfWritingDeadMemberRow() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		// 手填 ID 路径的防线：用户域查不到就拒绝，绝不静默落一条对方永远无感知的成员行
		when(remoteMallUserService.getUserById("ghost")).thenReturn(null);

		assertThatThrownBy(() -> service.inviteMember(TENANT, "cart-1", "user-1", "ghost", "2"))
			.isInstanceOfSatisfying(ArynBusinessException.class, ex -> assertThat(ex.getMsg()).contains("不存在"));
		verify(sharedCartMemberMapper, never()).insert(any(SharedCartMember.class));
		verify(inviteNotifyService, never()).notifyInvited(any(), any(), anyBoolean(), any(), any(), any());
	}

	@Test
	void inviteRejectsCrossTenantUser() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteMallUserService.getUserById("user-2")).thenReturn(mallUser("user-2", "other-tenant"));

		assertThatThrownBy(() -> service.inviteMember(TENANT, "cart-1", "user-1", "user-2", "2"))
			.isInstanceOfSatisfying(ArynBusinessException.class, ex -> assertThat(ex.getMsg()).contains("不属于当前企业"));
		verify(sharedCartMemberMapper, never()).insert(any(SharedCartMember.class));
	}

	@Test
	void inviteWritesMemberAndNotifiesInvitedUser() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(remoteMallUserService.getUserById("user-2")).thenReturn(mallUser("user-2", TENANT));
		// 成员去重检查：无重复
		when(sharedCartMemberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
		// 通知里的船名走展示快照，查不到允许为 null（不阻断邀请）
		when(remoteVesselService.getVesselCallSnapshot(TENANT, "call-old")).thenReturn(null);

		SharedCartMember member = service.inviteMember(TENANT, "cart-1", "user-1", "user-2", "3");

		assertThat(member.getUserId()).isEqualTo("user-2");
		assertThat(member.getCanConfirm()).isEqualTo("1");
		// 单测无事务同步，sendAfterCommit 立即执行：邀请必须让对方知道（此前完全不通知）
		verify(inviteNotifyService).notifyInvited(TENANT, "cart-1", false, null, "user-2", "李四");
	}

	@Test
	void shoreCartCandidatesSearchTenantUsersAndMaskPhone() {
		SharedCart cart = shoreCollectingCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		// 已有成员（发起人本人）：用于标记 alreadyMember
		when(sharedCartMemberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cartMember("user-1")));
		UserInfoVO candidate = mallUser("user-2", TENANT);
		candidate.setNickname("李四");
		candidate.setPhone("13812345678");
		candidate.setAvatarUrl("https://cdn/a.png");
		UserInfoVO owner = mallUser("user-1", TENANT);
		owner.setNickname("张三");
		owner.setPhone("13900001111");
		when(remoteMallUserService.searchTenantUsers(TENANT, "李四", 20)).thenReturn(List.of(candidate, owner));

		List<SharedCartMemberCandidateVO> result = service.listMemberCandidates(TENANT, "user-1", "cart-1", "李四");

		assertThat(result).hasSize(2);
		assertThat(result.get(0).getUserId()).isEqualTo("user-2");
		assertThat(result.get(0).getAlreadyMember()).isFalse();
		// 手机号出参必须脱敏，明文不出服务端
		assertThat(result.get(0).getPhone()).isEqualTo("138****5678");
		assertThat(result.get(1).getAlreadyMember()).isTrue();
	}

	@Test
	void shoreCartCandidatesRequireKeyword() {
		SharedCart cart = shoreCollectingCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);

		// 岸上车没有「同船成员」天然候选集：空关键词返回空列表而非全租户用户
		assertThat(service.listMemberCandidates(TENANT, "user-1", "cart-1", " ")).isEmpty();
		verify(remoteMallUserService, never()).searchTenantUsers(any(), any(), anyInt());
	}

	@Test
	void shipCartCandidatesComeFromOnboardVesselMembers() {
		SharedCart cart = collectingCart("call-old");
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		when(sharedCartMemberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cartMember("user-1")));
		VesselMemberVO crew = new VesselMemberVO();
		crew.setUserId("user-2");
		crew.setNickname("王五");
		// 船舶域出参已脱敏：订单域原样透传，不再二次处理
		crew.setPhone("139****0000");
		when(remoteVesselService.listOnboardMembers(TENANT, "vessel-1", null)).thenReturn(List.of(crew));

		List<SharedCartMemberCandidateVO> result = service.listMemberCandidates(TENANT, "user-1", "cart-1", null);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getUserId()).isEqualTo("user-2");
		assertThat(result.get(0).getPhone()).isEqualTo("139****0000");
		assertThat(result.get(0).getAlreadyMember()).isFalse();
		// 船供车不查用户域租户搜索
		verify(remoteMallUserService, never()).searchTenantUsers(any(), any(), anyInt());
	}

	private UserInfoVO mallUser(String userId, String tenantId) {
		UserInfoVO user = new UserInfoVO();
		user.setId(userId);
		user.setNickname("李四");
		user.setTenantId(tenantId);
		return user;
	}

	private SharedCartMember cartMember(String userId) {
		SharedCartMember member = new SharedCartMember();
		member.setCartId("cart-1");
		member.setUserId(userId);
		member.setMemberRole(SharedCartMember.ROLE_OWNER);
		member.setCanEdit("1");
		member.setCanConfirm("1");
		member.setTenantId(TENANT);
		return member;
	}

	private SharedCart shoreCollectingCart() {
		SharedCart cart = new SharedCart();
		cart.setId("cart-1");
		cart.setCartNo("SC-1");
		cart.setTenantId(TENANT);
		cart.setCartType(SharedCart.CART_TYPE_SHORE);
		cart.setVesselId(null);
		cart.setUserAddressId("addr-1");
		cart.setAddressSnapshot("江苏省南通市崇川区港闸路1号 张三 13800000000");
		cart.setOwnerUserId("user-1");
		cart.setConfirmerUserId("user-1");
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		cart.setExpiresAt(LocalDateTime.now().plusHours(2));
		cart.setDelFlag("0");
		return cart;
	}

	private UserAddress shoreAddress() {
		UserAddress address = new UserAddress();
		address.setId("addr-1");
		address.setRecipientName("张三");
		address.setTelephone("13800000000");
		address.setProvinceCode("320000");
		address.setCityCode("320600");
		address.setAreaCode("320602");
		address.setProvinceName("江苏省");
		address.setCityName("南通市");
		address.setAreaName("崇川区");
		address.setDetailAddress("港闸路1号");
		return address;
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
