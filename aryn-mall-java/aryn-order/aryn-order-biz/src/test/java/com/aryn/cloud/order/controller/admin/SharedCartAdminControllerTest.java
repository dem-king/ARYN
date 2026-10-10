package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端共享购物车列表展示口径：运营要看「哪条船、谁发起、生成哪张单」，
 * 而不是 vesselCallId/ownerUserId/submitOrderId 三个雪花 ID（2026-10-09）。
 */
class SharedCartAdminControllerTest {

	private SharedCartMapper sharedCartMapper;

	private SharedCartMemberMapper sharedCartMemberMapper;

	private SharedCartItemMapper sharedCartItemMapper;

	private OrderInfoMapper orderInfoMapper;

	private ISharedCartService sharedCartService;

	private RemoteVesselService remoteVesselService;

	private RemoteMallUserService remoteMallUserService;

	private SharedCartAdminController controller;

	@BeforeEach
	void setUp() {
		sharedCartMapper = mock(SharedCartMapper.class);
		sharedCartMemberMapper = mock(SharedCartMemberMapper.class);
		sharedCartItemMapper = mock(SharedCartItemMapper.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		sharedCartService = mock(ISharedCartService.class);
		remoteVesselService = mock(RemoteVesselService.class);
		remoteMallUserService = mock(RemoteMallUserService.class);
		controller = new SharedCartAdminController(sharedCartMapper, sharedCartMemberMapper, sharedCartItemMapper,
				orderInfoMapper, sharedCartService);
		// @DubboReference 字段不走构造器，测试里反射注入
		ReflectionTestUtils.setField(controller, "remoteVesselService", remoteVesselService);
		ReflectionTestUtils.setField(controller, "remoteMallUserService", remoteMallUserService);
		// 分页按租户过滤，展示快照也带租户：不设置会以 null 租户去查远程
		ArynTenantContextHolder.setTenantId("tenant-1");
	}

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("列表回填船名/港口、发起人姓名与订单号，不裸露内部ID")
	void pageFillsVesselOwnerAndOrderNo() {
		SharedCart cart = submittedCart();
		givenPage(cart);
		givenOwnerMember("cart-1", "owner-1", "张伟");
		// 历史单的靠港已结束：展示快照照样返回名称（走可下单查询会得到 null）
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-1")).thenReturn(staleContext());
		when(remoteMallUserService.getUserByIds(List.of("owner-1"))).thenReturn(List.of(user("owner-1", "张三")));
		when(orderInfoMapper.selectByIds(List.of("order-1"))).thenReturn(List.of(order("order-1", "2026100900001")));

		Map<String, Object> row = firstRow();

		assertThat(row.get("vesselCallText")).isEqualTo("悦航2号 · 福建港 321");
		// 自填姓名优先于商城昵称，与成员页/明细「来源成员」同一称呼
		assertThat(row.get("ownerName")).isEqualTo("张伟");
		assertThat(row.get("submitOrderNo")).isEqualTo("2026100900001");
	}

	@Test
	@DisplayName("成员未填姓名时发起人回落商城昵称")
	void pageOwnerFallsBackToNickname() {
		SharedCart cart = submittedCart();
		givenPage(cart);
		givenOwnerMember("cart-1", "owner-1", null);
		when(remoteVesselService.getVesselCallSnapshot(anyString(), anyString())).thenReturn(staleContext());
		when(remoteMallUserService.getUserByIds(anyList())).thenReturn(List.of(user("owner-1", "八度的骄傲")));
		when(orderInfoMapper.selectByIds(anyList())).thenReturn(List.of());

		assertThat(firstRow().get("ownerName")).isEqualTo("八度的骄傲");
	}

	@Test
	@DisplayName("列表不下发 shareToken：加入凭证对管理端列表没有用处")
	void pageOmitsShareToken() {
		SharedCart cart = submittedCart();
		cart.setShareToken("secret-token");
		givenPage(cart);
		givenOwnerMember("cart-1", "owner-1", "张伟");
		when(remoteVesselService.getVesselCallSnapshot(anyString(), anyString())).thenReturn(staleContext());
		when(remoteMallUserService.getUserByIds(anyList())).thenReturn(List.of());
		when(orderInfoMapper.selectByIds(anyList())).thenReturn(List.of());

		assertThat(firstRow()).doesNotContainKey("shareToken");
	}

	@Test
	@DisplayName("船舶域不可用时列表其余列照常，靠港列由前端回落 ID")
	void pageDegradesWhenVesselServiceFails() {
		SharedCart cart = submittedCart();
		givenPage(cart);
		givenOwnerMember("cart-1", "owner-1", "张伟");
		when(remoteVesselService.getVesselCallSnapshot(anyString(), anyString()))
				.thenThrow(new RuntimeException("vessel down"));
		when(remoteMallUserService.getUserByIds(anyList())).thenReturn(List.of());
		when(orderInfoMapper.selectByIds(anyList())).thenReturn(List.of());

		Map<String, Object> row = firstRow();

		assertThat(row.get("vesselCallText")).isNull();
		// 运营仍能拿到原始 ID 用于排障，且同一行的其它回填不受影响
		assertThat(row.get("vesselCallId")).isEqualTo("call-1");
		assertThat(row.get("ownerName")).isEqualTo("张伟");
	}

	@Test
	@DisplayName("姓名全链路缺失时回落「用户+ID后6位」，与详情页同一兜底链")
	void pageFallsBackWhenNamesMissing() {
		SharedCart cart = submittedCart();
		// 用真实形态的雪花 ID：兜底取后 6 位，运营可据此在用户模块核对具体人
		cart.setOwnerUserId("2103140502970413057");
		givenPage(cart);
		givenOwnerMember("cart-1", "2103140502970413057", null);
		when(remoteVesselService.getVesselCallSnapshot(anyString(), anyString())).thenReturn(staleContext());
		// 用户域返回空表（远程抖动降级），订单查不到（订单已物理清理等）
		when(remoteMallUserService.getUserByIds(anyList())).thenReturn(List.of());
		when(orderInfoMapper.selectByIds(anyList())).thenReturn(List.of());

		Map<String, Object> row = firstRow();

		assertThat(row.get("ownerName")).isEqualTo("用户413057");
		assertThat(row.get("submitOrderNo")).isNull();
	}

	@Test
	@DisplayName("空页不发起任何回填查询")
	void emptyPageSkipsEnrichment() {
		Page<SharedCart> empty = new Page<>(1, 10);
		empty.setRecords(List.of());
		when(sharedCartMapper.selectPage(any(), any(Wrapper.class))).thenReturn(empty);

		controller.page(new Page<>(1, 10), new SharedCart());

		verify(sharedCartMemberMapper, never()).selectList(any(Wrapper.class));
		verify(remoteVesselService, never()).getVesselCallSnapshot(anyString(), anyString());
		verify(remoteMallUserService, never()).getUserByIds(anyList());
		verify(orderInfoMapper, never()).selectByIds(anyList());
	}

	@Test
	@DisplayName("成员页与明细来源成员与列表发起人用同一称呼")
	void detailUsesSameDisplayNameAcrossMembersAndItems() {
		SharedCart cart = submittedCart();
		when(sharedCartMapper.selectOne(any(Wrapper.class))).thenReturn(cart);
		// 张伟自有 1 条明细；李强是另一位成员，也自填了姓名
		when(sharedCartMemberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
				member("cart-1", "owner-1", SharedCartMember.ROLE_OWNER, "张伟"),
				member("cart-1", "member-2", SharedCartMember.ROLE_MEMBER, "李强")));
		when(sharedCartItemMapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(item("item-1", "owner-1"), item("item-2", "member-2")));
		// 两人的商城昵称都是脱敏手机号：若回填链路漏了自填姓名就会显示成它
		when(remoteMallUserService.getUserByIds(anyList())).thenReturn(
				List.of(user("owner-1", "176****2320"), user("member-2", "199****1234")));

		Result<Map<String, Object>> result = controller.detail("cart-1");

		@SuppressWarnings("unchecked")
		List<SharedCartMember> members = (List<SharedCartMember>) result.getData().get("members");
		assertThat(members).extracting(SharedCartMember::getDisplayName)
				.containsExactly("张伟", "李强");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) result.getData().get("items");
		assertThat(items).extracting(row -> row.get("contributorName"))
				.containsExactly("张伟", "李强");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> firstRow() {
		Result<IPage<Map<String, Object>>> result = controller.page(new Page<>(1, 10), new SharedCart());
		return result.getData().getRecords().get(0);
	}

	private void givenPage(SharedCart cart) {
		Page<SharedCart> page = new Page<>(1, 10);
		page.setRecords(List.of(cart));
		when(sharedCartMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
	}

	/** 发起人在成员表里有一行（创建时写入），其 display_name 即自填姓名 */
	private void givenOwnerMember(String cartId, String userId, String displayName) {
		when(sharedCartMemberMapper.selectList(any(Wrapper.class)))
				.thenReturn(List.of(member(cartId, userId, SharedCartMember.ROLE_OWNER, displayName)));
	}

	private SharedCart submittedCart() {
		SharedCart cart = new SharedCart();
		cart.setId("cart-1");
		cart.setCartNo("SC-1");
		cart.setTenantId("tenant-1");
		cart.setVesselId("vessel-1");
		cart.setVesselCallId("call-1");
		cart.setOwnerUserId("owner-1");
		cart.setStatus(SharedCart.STATUS_SUBMITTED);
		cart.setSubmitOrderId("order-1");
		return cart;
	}

	private SharedCartMember member(String cartId, String userId, String role, String displayName) {
		SharedCartMember member = new SharedCartMember();
		member.setCartId(cartId);
		member.setUserId(userId);
		member.setMemberRole(role);
		member.setDisplayName(displayName);
		return member;
	}

	private SharedCartItem item(String id, String userId) {
		SharedCartItem item = new SharedCartItem();
		item.setId(id);
		item.setCartId("cart-1");
		item.setUserId(userId);
		item.setSpuId("spu-1");
		item.setSkuId("sku-1");
		item.setRequestedQuantity(1);
		return item;
	}

	/** 已结束的靠港：展示字段齐全，但已不可下单 */
	private VesselContextDTO staleContext() {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId("vessel-1");
		context.setVesselName("悦航2号");
		context.setVesselCallId("call-1");
		context.setPortName("福建港");
		context.setBerth("321");
		context.setCallOrderable(Boolean.FALSE);
		return context;
	}

	private UserInfoVO user(String id, String nickname) {
		UserInfoVO user = new UserInfoVO();
		user.setId(id);
		user.setNickname(nickname);
		return user;
	}

	private OrderInfo order(String id, String orderNo) {
		OrderInfo order = new OrderInfo();
		order.setId(id);
		order.setOrderNo(orderNo);
		return order;
	}

}
