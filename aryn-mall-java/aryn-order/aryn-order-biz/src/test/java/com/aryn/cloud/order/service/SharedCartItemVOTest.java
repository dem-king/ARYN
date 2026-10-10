package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartItemVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 共享购物车明细 VО（C 端清单）契约测试。
 *
 * <p>背景（2026-10-10）：明细接口原来直接回实体，而实体只存 SPU/SKU ID 与数量 ——
 * C 端清单既显示不出商品名，也显示不出价格，确认人提交前不知道这批要花多少钱。
 * 现在明细经商品域补齐名称/规格/图片/单价，本测试守三件事：
 * <ol>
 *   <li>取值口径：数量按成员申请量，不拿核定数量冒充「已买多少」；</li>
 *   <li>降级口径：商品域不可用时名称与单价为 null，**不能**回落成 0 元
 *       （0 会被读成「不要钱」，把没算到的钱藏起来）；</li>
 *   <li>空清单不报错，返回空列表。</li>
 * </ol>
 */
class SharedCartItemVOTest {

	private static final String TENANT = "tenant-1";

	private static final String CART_ID = "cart-1";

	private SharedCartItemMapper itemMapper;

	private SharedCartMemberMapper memberMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private RemoteGoodsSpuService remoteGoodsSpuService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		itemMapper = mock(SharedCartItemMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		when(memberMapper.selectList(any())).thenReturn(List.of());
		service = new SharedCartServiceImpl(mock(SharedCartMapper.class), memberMapper, itemMapper,
				mock(SharedCartImportMapper.class), mock(SharedCartImportRowMapper.class),
				mock(IOrderInfoService.class));
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", remoteGoodsSkuService);
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", remoteGoodsSpuService);
		ReflectionTestUtils.setField(service, "remoteVesselService", mock(RemoteVesselService.class));
		ReflectionTestUtils.setField(service, "remoteMallUserService", mock(RemoteMallUserService.class));
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private SharedCartItem item(String itemId, String skuId, int requested, Integer approved) {
		SharedCartItem item = new SharedCartItem();
		item.setId(itemId);
		item.setCartId(CART_ID);
		item.setUserId("user-1");
		item.setSpuId("spu-" + skuId);
		item.setSkuId(skuId);
		item.setRequestedQuantity(requested);
		item.setApprovedQuantity(approved);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		return item;
	}

	/**
	 * SKU 只带价格：**刻意不塞 goodsSpu**。
	 *
	 * <p>真实链路的 {@code getSkuByIds} 走 goodsSkuResultMap，不回填该关联；
	 * 塞上假 SPU 会把「名称恒为 null」的线上问题在单测里盖住。
	 */
	private GoodsSku sku(String skuId, String price) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSpuId("spu-" + skuId);
		if (price != null) {
			sku.setSalesPrice(new BigDecimal(price));
		}
		return sku;
	}

	private GoodsSpu spu(String spuId, String name) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(spuId);
		spu.setName(name);
		return spu;
	}

	@Test
	@DisplayName("补齐商品名、规格、图片与单价：C 端清单不再只显示 ID 与数量")
	void enrichesNamePriceAndPicture() {
		when(itemMapper.selectList(any())).thenReturn(List.of(item("i1", "sku-1", 2, null)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenReturn(List.of(sku("sku-1", "12.50")));
		when(remoteGoodsSpuService.getSpuByIds(anyList()))
				.thenReturn(List.of(spu("spu-sku-1", "流油咸鸭蛋8枚")));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		assertThat(list).hasSize(1);
		SharedCartItemVO vo = list.get(0);
		assertThat(vo.getSpuName()).isEqualTo("流油咸鸭蛋8枚");
		assertThat(vo.getUnitPrice()).isEqualByComparingTo("12.50");
		assertThat(vo.getRequestedQuantity()).isEqualTo(2);
		// 金额不在服务端下发：页面还要跟随确认人改核定数量实时重算，
		// 后端给一个固定快照只会让两处数字打架（口径见前端 pricedQuantityOf）
		assertThat(vo.getApprovedQuantity()).isNull();
	}

	@Test
	@DisplayName("成员名从成员表带出：明细行不必只显示用户 ID")
	void keepsContributorVisibleThroughMemberList() {
		SharedCartMember member = new SharedCartMember();
		member.setCartId(CART_ID);
		member.setUserId("user-1");
		member.setDisplayName("李强");
		member.setMemberRole(SharedCartMember.ROLE_MEMBER);
		when(memberMapper.selectList(any())).thenReturn(List.of(member));
		when(itemMapper.selectList(any())).thenReturn(List.of(item("i1", "sku-1", 1, null)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenReturn(List.of(sku("sku-1", "1")));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		// 明细归属者的姓名由前端用成员列表对照展示；这里守住「成员行本身可取到姓名」，
		// 否则清单里的「来自 XXX」会退化成一串用户 ID。
		assertThat(list.get(0).getUserId()).isEqualTo("user-1");
		assertThat(member.getDisplayName()).isEqualTo("李强");
	}

	@Test
	@DisplayName("商品域不可用：名称与单价为 null，不回落成 0 元")
	void degradesWithoutFakingZeroPrice() {
		when(itemMapper.selectList(any())).thenReturn(List.of(item("i1", "sku-1", 2, null)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenThrow(new RuntimeException("dubbo down"));
		when(remoteGoodsSpuService.getSpuByIds(anyList())).thenThrow(new RuntimeException("dubbo down"));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		// 明细本身要照常返回（供前端用 SKU 编号兜底展示），但价格必须是 null ——
		// 回落成 0 会让合计看起来是真价，把没算到的钱藏起来
		assertThat(list).hasSize(1);
		assertThat(list.get(0).getSpuName()).isNull();
		assertThat(list.get(0).getUnitPrice()).isNull();
	}

	@Test
	@DisplayName("SKU 已下架查不到：该行名称与单价为 null，其余行照常")
	void keepsRowWithoutSkuButDropsItsPrice() {
		when(itemMapper.selectList(any()))
				.thenReturn(List.of(item("i1", "sku-1", 1, null), item("i2", "sku-gone", 1, null)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenReturn(List.of(sku("sku-1", "5.00")));
		when(remoteGoodsSpuService.getSpuByIds(anyList()))
				.thenReturn(List.of(spu("spu-sku-1", "在售商品"), spu("spu-sku-gone", "已下架商品")));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		assertThat(list).hasSize(2);
		assertThat(list.get(0).getUnitPrice()).isEqualByComparingTo("5.00");
		// SPU 还在（所以有名字），但 SKU 已经被删 → 取不到价，仍必须是 null
		assertThat(list.get(1).getSpuName()).isEqualTo("已下架商品");
		assertThat(list.get(1).getUnitPrice()).isNull();
	}

	@Test
	@DisplayName("核定数量原样下发：前端据此标「本次不采」并能算准金额")
	void passesApprovedQuantityThrough() {
		when(itemMapper.selectList(any()))
				.thenReturn(List.of(item("i1", "sku-1", 3, 0), item("i2", "sku-2", 2, 5)));
		when(remoteGoodsSkuService.getSkuByIds(anyList()))
				.thenReturn(List.of(sku("sku-1", "10.00"), sku("sku-2", "2.00")));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		assertThat(list.get(0).getApprovedQuantity()).isZero();
		assertThat(list.get(1).getApprovedQuantity()).isEqualTo(5);
		// 申请量保持原值：核对「报了多少 / 核定多少」需要两个数都在
		assertThat(list.get(1).getRequestedQuantity()).isEqualTo(2);
	}

	@Test
	@DisplayName("单价为 0 的在售商品不被当成缺价")
	void keepsZeroPriceAsRealPrice() {
		when(itemMapper.selectList(any())).thenReturn(List.of(item("i1", "sku-free", 1, null)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenReturn(List.of(sku("sku-free", "0")));

		List<SharedCartItemVO> list = service.listItemVOs(TENANT, CART_ID);

		assertThat(list.get(0).getUnitPrice()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("空清单返回空列表，不去查商品域")
	void emptyCartReturnsEmptyList() {
		when(itemMapper.selectList(any())).thenReturn(List.of());

		assertThat(service.listItemVOs(TENANT, CART_ID)).isEmpty();
	}

	@Test
	@DisplayName("展示口径不按状态过滤：已提交的单明细照样看得到（提交时行已置为已确认）")
	void showsConfirmedRowsOfASubmittedCart() {
		// 提交会把每行置为 ITEM_CONFIRMED；只取 ITEM_PENDING 的话，
		// 详情页在提交后立刻变成「暂无明细」，而列表卡片还写着「6 项」，自相矛盾。
		when(itemMapper.selectList(any())).thenReturn(List.of());
		service.listItemVOs(TENANT, CART_ID);

		ArgumentCaptor<Wrapper<SharedCartItem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(itemMapper).selectList(wrapperCaptor.capture());
		String sql = wrapperCaptor.getValue().getSqlSegment();
		// 只按「不等于已移除」过滤：写成 status = 待确认 就会把提交后的行全部藏起来
		// （del_flag 由逻辑删除拦截器补，不在 wrapper 段里）
		assertThat(sql).contains("status <");
		assertThat(sql).doesNotContain("status =");
	}

}
