package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartTransferDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingCartServiceImplTest {

	private ShoppingCartMapper mapper;
	private RemoteGoodsSkuService remoteGoodsSkuService;
	private RemoteGoodsSpuService remoteGoodsSpuService;
	private RemoteVesselService remoteVesselService;
	private ShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShoppingCart.class);
		ArynTenantContextHolder.setTenantId("tenant-1");
		mapper = mock(ShoppingCartMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		remoteVesselService = mock(RemoteVesselService.class);
		service = new TestShoppingCartService(remoteGoodsSkuService, remoteGoodsSpuService, mapper);
		ReflectionTestUtils.setField(service, "remoteVesselService", remoteVesselService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void addUsesServerSideSkuSnapshot() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.insert(any(ShoppingCart.class))).thenReturn(1);
		ShoppingCartCreateDTO request = new ShoppingCartCreateDTO();
		request.setSkuId("sku-1");
		request.setQuantity(2);

		assertThat(service.saveShoppingCart("user-1", request)).isTrue();

		ArgumentCaptor<ShoppingCart> captor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(mapper).insert(captor.capture());
		ShoppingCart saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo("user-1");
		assertThat(saved.getSpuId()).isEqualTo("spu-1");
		assertThat(saved.getSpuName()).isEqualTo("服务端商品");
		assertThat(saved.getSalesPrice()).isEqualByComparingTo("19.90");
		assertThat(saved.getPicUrl()).isEqualTo("sku.png");
		assertThat(saved.getSpecsInfo()).isEqualTo("红色");
	}

	/**
	 * 真实缺陷回归：靠港结束后客户端靠港快照被清空，再买同款只能落「无归属」组；
	 * 若只按靠港合并，购物车会出现同款两行（滞留行 + 无归属行），结算撞「重复 SKU」。
	 * 同船存在已结束靠港的滞留行时，本次加购应并入该行累加数量，不再插入新行。
	 */
	@Test
	void addMergesIntoStrandedRowOfSameVesselWhenCallContextIsGone() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(strandedRow("cart-old", "call-old")));
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-old"))
			.thenReturn(vesselContext("call-old", "上海港", "悦航7号", false));
		when(mapper.incrementQuantityById("user-1", "cart-old", 2)).thenReturn(1);

		assertThat(service.saveShoppingCart("user-1", addRequest("vessel-1", null, 2))).isTrue();

		verify(mapper).incrementQuantityById("user-1", "cart-old", 2);
		verify(mapper, never()).insert(any(ShoppingCart.class));
	}

	/**
	 * 同船靠港仍在进行中（callOrderable=true）：新加购可能属于下一港，不能跨靠港并入，
	 * 照常新建一行（防串船是原有语义，不能被滞留行逻辑放宽）。
	 */
	@Test
	void addCreatesNewRowWhenSameVesselCallStillOrderable() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(strandedRow("cart-active", "call-active")));
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-active"))
			.thenReturn(vesselContext("call-active", "上海港", "悦航7号", true));
		when(mapper.insert(any(ShoppingCart.class))).thenReturn(1);

		assertThat(service.saveShoppingCart("user-1", addRequest("vessel-1", null, 2))).isTrue();

		verify(mapper).insert(any(ShoppingCart.class));
		verify(mapper, never()).incrementQuantityById(any(), any(), anyInt());
	}

	/**
	 * 判断滞留行要靠船舶域远程调用：超时/异常时无法判定靠港是否已结束，
	 * 降级为照常新建行（宁可多一行，也不误并入仍在进行中的靠港）。
	 */
	@Test
	void addFallsBackToNewRowWhenStrandedSnapshotLookupFails() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(strandedRow("cart-old", "call-old")));
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-old"))
			.thenThrow(new RuntimeException("dubbo timeout"));
		when(mapper.insert(any(ShoppingCart.class))).thenReturn(1);

		assertThat(service.saveShoppingCart("user-1", addRequest("vessel-1", null, 2))).isTrue();

		verify(mapper).insert(any(ShoppingCart.class));
		verify(mapper, never()).incrementQuantityById(any(), any(), anyInt());
	}

	/**
	 * 请求未带 vesselId（无法定位是哪条船的滞留行）：不做滞留行查询，照常新建。
	 */
	@Test
	void addSkipsStrandedLookupWhenVesselIdMissing() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.insert(any(ShoppingCart.class))).thenReturn(1);

		assertThat(service.saveShoppingCart("user-1", addRequest(null, null, 2))).isTrue();

		verify(mapper).insert(any(ShoppingCart.class));
		verify(mapper, never()).selectList(any(Wrapper.class));
		verify(mapper, never()).incrementQuantityById(any(), any(), anyInt());
	}


	@Test
	void quantityUpdateRevalidatesStockBeforeWriting() {
		ShoppingCart source = new ShoppingCart();
		source.setId("cart-1");
		source.setUserId("user-1");
		source.setSkuId("sku-1");
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(source);
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(1)));
		ShoppingCartUpdateDTO request = new ShoppingCartUpdateDTO();
		request.setId("cart-1");
		request.setQuantity(2);

		assertThatThrownBy(() -> service.updateShoppingCart("user-1", request))
			.isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).updateById(any(ShoppingCart.class));
	}

	@Test
	void quantityUpdateRefreshesSnapshot() {
		ShoppingCart source = new ShoppingCart();
		source.setId("cart-1");
		source.setUserId("user-1");
		source.setSkuId("sku-1");
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(source);
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.updateById(any(ShoppingCart.class))).thenReturn(1);
		ShoppingCartUpdateDTO request = new ShoppingCartUpdateDTO();
		request.setId("cart-1");
		request.setQuantity(3);

		assertThat(service.updateShoppingCart("user-1", request)).isTrue();

		ArgumentCaptor<ShoppingCart> captor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(mapper).updateById(captor.capture());
		assertThat(captor.getValue().getQuantity()).isEqualTo(3);
		assertThat(captor.getValue().getSpuName()).isEqualTo("服务端商品");
		assertThat(captor.getValue().getSalesPrice()).isEqualByComparingTo("19.90");
	}

	/**
	 * 购物车里的商品可能已下架/删除，此时商品服务查不到该 SKU —— 这是正常业务状态，
	 * 不是错误。前端据 goodsSku 为空展示「下架」并让用户自行清理。
	 *
	 * <p>回归背景（真实缺陷）：此处原先直接抛 IllegalArgumentException，
	 * 导致「整车商品都已下架」的用户打开购物车页得到 HTTP 500，
	 * 连清空购物车的入口都进不去。
	 */
	@Test
	void cartListToleratesEntirelyOffShelfSkus() {
		when(mapper.selectApiPage(any(), any(ShoppingCart.class))).thenReturn(pageOf(cartRow("cart-1", "sku-gone")));
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-gone"))).thenReturn(List.of());

		List<ShoppingCart> rows = service.apiPage(new Page<>(1, 10), new ShoppingCart());

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getGoodsSku()).isNull();
	}

	/**
	 * 部分下架：在售行照常带出 SKU 详情，下架行留空，不影响同车其他商品结算。
	 */
	@Test
	void cartListKeepsSnapshotForSellableRowsWhenOthersOffShelf() {
		when(mapper.selectApiPage(any(), any(ShoppingCart.class)))
			.thenReturn(pageOf(cartRow("cart-1", "sku-1"), cartRow("cart-2", "sku-gone")));
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1", "sku-gone"))).thenReturn(List.of(saleSku(10)));

		List<ShoppingCart> rows = service.apiPage(new Page<>(1, 10), new ShoppingCart());

		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).getGoodsSku()).isNotNull();
		assertThat(rows.get(0).getGoodsSku().getId()).isEqualTo("sku-1");
		assertThat(rows.get(1).getGoodsSku()).isNull();
	}

	private static Page<ShoppingCart> pageOf(ShoppingCart... rows) {
		Page<ShoppingCart> page = new Page<>(1, 10);
		page.setRecords(List.of(rows));
		return page;
	}

	private static ShoppingCart cartRow(String id, String skuId) {
		ShoppingCart row = new ShoppingCart();
		row.setId(id);
		row.setUserId("user-1");
		row.setSkuId(skuId);
		return row;
	}

	private static ShoppingCartCreateDTO addRequest(String vesselId, String vesselCallId, int quantity) {
		ShoppingCartCreateDTO request = new ShoppingCartCreateDTO();
		request.setSkuId("sku-1");
		request.setQuantity(quantity);
		request.setVesselId(vesselId);
		request.setVesselCallId(vesselCallId);
		return request;
	}

	private static ShoppingCart strandedRow(String id, String vesselCallId) {
		ShoppingCart row = new ShoppingCart();
		row.setId(id);
		row.setUserId("user-1");
		row.setSkuId("sku-1");
		row.setVesselId("vessel-1");
		row.setVesselCallId(vesselCallId);
		row.setQuantity(1);
		return row;
	}

	@Test
	void cartPageMasksCostPriceOnAttachedSku() {
		Page<ShoppingCart> page = new Page<>(1, 10);
		page.setRecords(List.of(cartRow("cart-1", "sku-1")));
		when(mapper.selectApiPage(any(Page.class), any(ShoppingCart.class))).thenReturn(page);
		GoodsSku sku = saleSku(10);
		sku.setCostPrice(new BigDecimal("6.50"));
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1"))).thenReturn(List.of(sku));

		List<ShoppingCart> result = service.apiPage(page, new ShoppingCart());

		// 购物车行内嵌完整 GoodsSku，成本价属内部经营数据，出参必须清空；售价保留供展示
		assertThat(result.get(0).getGoodsSku().getCostPrice()).isNull();
		assertThat(result.get(0).getGoodsSku().getSalesPrice()).isEqualByComparingTo("19.90");
	}

	@Test
	void cartPageCarriesSpuSubTitleForCardIntro() {
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setSpuId("spu-1");
		Page<ShoppingCart> page = pageOf(row);
		when(mapper.selectApiPage(any(Page.class), any(ShoppingCart.class))).thenReturn(page);
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1")))
			.thenReturn(List.of(new GoodsSpu().setId("spu-1").setSubTitle("酸酸甜甜｜个大会爆浆")));

		List<ShoppingCart> result = service.apiPage(page, new ShoppingCart());

		assertThat(result.get(0).getSubTitle()).isEqualTo("酸酸甜甜｜个大会爆浆");
	}

	/**
	 * 卖点只是展示信息：商品服务超时不能把整车列表拖垮，降级为不展示即可。
	 */
	@Test
	void cartPageDegradesWhenSubTitleLookupFails() {
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setSpuId("spu-1");
		Page<ShoppingCart> page = pageOf(row);
		when(mapper.selectApiPage(any(Page.class), any(ShoppingCart.class))).thenReturn(page);
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1"))).thenThrow(new RuntimeException("dubbo timeout"));
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));

		List<ShoppingCart> result = service.apiPage(page, new ShoppingCart());

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getSubTitle()).isNull();
	}

	/**
	 * 分组头可读化：行归属靠港的港口/船名/是否已结束按 vesselCallId 批量回填
	 * （展示快照口径，已结束的靠港也有值），C 端据此替代裸靠港 ID。
	 */
	@Test
	void cartPageBackfillsVesselCallSnapshotForGroupHeading() {
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setVesselCallId("call-1");
		Page<ShoppingCart> page = pageOf(row);
		when(mapper.selectApiPage(any(Page.class), any(ShoppingCart.class))).thenReturn(page);
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-1"))
			.thenReturn(vesselContext("call-1", "上海港", "悦航7号", false));

		List<ShoppingCart> result = service.apiPage(page, new ShoppingCart());

		assertThat(result.get(0).getVesselCallPortName()).isEqualTo("上海港");
		assertThat(result.get(0).getVesselCallVesselName()).isEqualTo("悦航7号");
		assertThat(result.get(0).getVesselCallOrderable()).isFalse();
	}

	/**
	 * 靠港快照只是展示信息：船舶域超时不能把购物车列表拖垮，
	 * 缺失行降级为空字段，前端回落到不含港口的通用分组文案。
	 */
	@Test
	void cartPageDegradesWhenVesselCallSnapshotFails() {
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setVesselCallId("call-1");
		Page<ShoppingCart> page = pageOf(row);
		when(mapper.selectApiPage(any(Page.class), any(ShoppingCart.class))).thenReturn(page);
		when(remoteGoodsSkuService.getSkuByIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(remoteVesselService.getVesselCallSnapshot("tenant-1", "call-1"))
			.thenThrow(new RuntimeException("dubbo timeout"));

		List<ShoppingCart> result = service.apiPage(page, new ShoppingCart());

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getVesselCallPortName()).isNull();
		assertThat(result.get(0).getVesselCallOrderable()).isNull();
	}

	/**
	 * 转入目标靠港必须仍可下单：挂上不可达的靠港也结不了算，当场拒绝。
	 */
	@Test
	void transferRejectsCallThatIsNoLongerOrderable() {
		when(remoteVesselService.getVesselCallContext("tenant-1", "call-2")).thenReturn(null);
		ShoppingCartTransferDTO request = transferDTO("call-2", "cart-1");

		assertThatThrownBy(() -> service.transferToVesselCall("user-1", request))
			.isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(isNull(), any(Wrapper.class));
	}

	/**
	 * 转入后目标靠港没有同 SKU 行：原行改挂目标靠港（vessel_id 一并刷成目标船）。
	 */
	@Test
	void transferMovesRowWhenTargetHasNoSameSkuRow() {
		when(remoteVesselService.getVesselCallContext("tenant-1", "call-2"))
			.thenReturn(vesselContext("call-2", "宁波港", "悦航7号", true));
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setUserId("user-1");
		row.setVesselId("vessel-1");
		row.setVesselCallId("call-1");
		row.setQuantity(2);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
		when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

		int moved = service.transferToVesselCall("user-1", transferDTO("call-2", "cart-1"));

		assertThat(moved).isEqualTo(1);
		ArgumentCaptor<Wrapper<ShoppingCart>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(isNull(), captor.capture());
		// set 系急切绑定：参数值渲染后必须带目标靠港
		assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue())
			.getParamNameValuePairs().values()).contains("call-2", "vessel-1");
		verify(mapper, never()).incrementQuantityById(any(), any(), org.mockito.ArgumentMatchers.anyInt());
		verify(mapper, never()).delete(any(Wrapper.class));
	}

	/**
	 * 用户已在新靠港重新加购过同商品：转入并入已有行（原子加量 + 源行删除），
	 * 不能撞 uk_shopping_cart_active_row 唯一键。
	 */
	@Test
	void transferMergesIntoExistingRowOnTargetCall() {
		when(remoteVesselService.getVesselCallContext("tenant-1", "call-2"))
			.thenReturn(vesselContext("call-2", "宁波港", "悦航7号", true));
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setUserId("user-1");
		row.setSkuId("sku-1");
		row.setVesselCallId("call-1");
		row.setQuantity(2);
		ShoppingCart existing = cartRow("cart-9", "sku-1");
		existing.setUserId("user-1");
		existing.setVesselCallId("call-2");
		existing.setQuantity(3);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);
		when(mapper.incrementQuantityById(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);
		when(mapper.delete(any(Wrapper.class))).thenReturn(1);

		int moved = service.transferToVesselCall("user-1", transferDTO("call-2", "cart-1"));

		assertThat(moved).isEqualTo(1);
		verify(mapper).incrementQuantityById("user-1", "cart-9", 2);
		verify(mapper).delete(any(Wrapper.class));
		verify(mapper, never()).update(isNull(), any(Wrapper.class));
	}

	/**
	 * 请求的行都已挂在目标靠港（重复提交）：没有可迁移行时给出明确业务提示。
	 */
	@Test
	void transferRejectsWhenNothingToMove() {
		when(remoteVesselService.getVesselCallContext("tenant-1", "call-2"))
			.thenReturn(vesselContext("call-2", "宁波港", "悦航7号", true));
		ShoppingCart row = cartRow("cart-1", "sku-1");
		row.setUserId("user-1");
		row.setVesselCallId("call-2");
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));

		assertThatThrownBy(() -> service.transferToVesselCall("user-1", transferDTO("call-2", "cart-1")))
			.isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(isNull(), any(Wrapper.class));
	}

	/**
	 * 靠港顺延（申报/排产生效）：无归属行改挂新靠港；同 SKU 的第二行并入
	 * 目标靠港已有行——旧实现是单条 UPDATE，撞唯一键整次失败且重试恒败。
	 */
	@Test
	void reattachMigratesStrandedRowsAndMergesDuplicates() {
		ShoppingCart stranded = cartRow("cart-1", "sku-1");
		stranded.setUserId("user-1");
		stranded.setSkuId("sku-1");
		stranded.setVesselId("vessel-1");
		stranded.setVesselCallId("call-old");
		stranded.setQuantity(1);
		ShoppingCart unattributed = cartRow("cart-2", "sku-1");
		unattributed.setUserId("user-1");
		unattributed.setSkuId("sku-1");
		unattributed.setVesselId("vessel-1");
		unattributed.setQuantity(2);
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(stranded, unattributed));
		// 第一行改挂时目标靠港无同 SKU 行；第二行迁移时第一行已在目标靠港
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(null, strandedAfterMove());
		when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
		when(mapper.incrementQuantityById(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);
		when(mapper.delete(any(Wrapper.class))).thenReturn(1);

		int moved = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-old"));

		assertThat(moved).isEqualTo(2);
		verify(mapper, times(1)).update(isNull(), any(Wrapper.class));
		verify(mapper, times(1)).incrementQuantityById("user-1", "cart-1", 2);
		verify(mapper, times(1)).delete(any(Wrapper.class));
	}

	/**
	 * 该船没有滞留行时顺延是空操作，不产生任何写。
	 */
	@Test
	void reattachDoesNothingWhenNoStrandedRows() {
		when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		int moved = service.reattachRowsToVesselCall("tenant-1", "vessel-1", "call-2", List.of("call-old"));

		assertThat(moved).isZero();
		verify(mapper, never()).update(isNull(), any(Wrapper.class));
		verify(mapper, never()).incrementQuantityById(any(), any(), org.mockito.ArgumentMatchers.anyInt());
	}

	private ShoppingCart strandedAfterMove() {
		ShoppingCart moved = cartRow("cart-1", "sku-1");
		moved.setUserId("user-1");
		moved.setSkuId("sku-1");
		moved.setVesselId("vessel-1");
		moved.setVesselCallId("call-2");
		moved.setQuantity(1);
		return moved;
	}

	private ShoppingCartTransferDTO transferDTO(String callId, String... rowIds) {
		ShoppingCartTransferDTO request = new ShoppingCartTransferDTO();
		request.setCallId(callId);
		request.setRowIds(List.of(rowIds));
		return request;
	}

	private VesselContextDTO vesselContext(String callId, String portName, String vesselName, boolean orderable) {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselCallId(callId);
		context.setVesselId("vessel-1");
		context.setVesselName(vesselName);
		context.setPortName(portName);
		context.setCallOrderable(orderable);
		return context;
	}

	private GoodsSku saleSku(int stock) {
		GoodsSpu spu = new GoodsSpu().setId("spu-1").setName("服务端商品")
			.setStatus("1").setSpuUrls(new String[] { "spu.png" });
		return new GoodsSku().setId("sku-1").setSpuId("spu-1").setStock(stock)
			.setSalesPrice(new BigDecimal("19.90")).setPicUrl("sku.png")
			.setSpecsArr(List.of(new GoodsSku.Specs("spec-1", "颜色", "value-1", "红色")))
			.setGoodsSpu(spu);
	}

	private static final class TestShoppingCartService extends ShoppingCartServiceImpl {

		private TestShoppingCartService(RemoteGoodsSkuService remoteGoodsSkuService,
				RemoteGoodsSpuService remoteGoodsSpuService, ShoppingCartMapper mapper) {
			super(remoteGoodsSkuService, remoteGoodsSpuService);
			this.baseMapper = mapper;
		}
	}
}
