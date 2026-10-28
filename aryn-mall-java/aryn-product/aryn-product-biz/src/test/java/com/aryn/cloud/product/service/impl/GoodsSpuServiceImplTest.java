package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.GoodsAttrTag;
import com.aryn.cloud.product.api.entity.GoodsCollect;
import com.aryn.cloud.product.api.entity.GoodsFootprint;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsCollectMapper;
import com.aryn.cloud.product.mapper.GoodsFootprintMapper;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSpuServiceImplTest {

	private GoodsSpuMapper spuMapper;

	private GoodsSkuMapper skuMapper;

	private GoodsFootprintMapper footprintMapper;

	private GoodsSpuServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsCollect.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsFootprint.class);
		spuMapper = mock(GoodsSpuMapper.class);
		skuMapper = mock(GoodsSkuMapper.class);
		footprintMapper = mock(GoodsFootprintMapper.class);
		service = new TestGoodsSpuService(skuMapper, mock(GoodsCollectMapper.class), footprintMapper,
				mock(GoodsCategoryMapper.class), spuMapper);
	}

	@Test
	void saveGoodsCalculatesPricesAndClearsServerOwnedFields() {
		when(spuMapper.insert(any(GoodsSpu.class))).thenAnswer(invocation -> {
			GoodsSpu saved = invocation.getArgument(0);
			saved.setId("spu-1");
			return 1;
		});
		when(skuMapper.insert(any(GoodsSku.class))).thenReturn(1);
		GoodsSku firstSku = sku(null, 20, 100, 60, 3, 0);
		firstSku.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "red", "红色")));
		GoodsSku secondSku = sku(null, 30, 80, 40, 5, 0);
		secondSku.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "blue", "蓝色")));
		GoodsSpu request = product(null, List.of(firstSku, secondSku));
		request.setId("client-spu-id");
		request.setTenantId("victim-tenant");
		request.setSalesVolume(999);
		request.getGoodsSkus().forEach(item -> item.setTenantId("victim-tenant"));

		assertThat(service.saveGoods(request)).isTrue();

		assertThat(request.getId()).isEqualTo("spu-1");
		assertThat(request.getTenantId()).isNull();
		assertThat(request.getSalesVolume()).isZero();
		assertThat(request.getSalesPrice()).isEqualByComparingTo("20");
		assertThat(request.getOriginalPrice()).isEqualByComparingTo("80");
		assertThat(request.getCostPrice()).isEqualByComparingTo("40");
		assertThat(request.getStock()).isEqualTo(8);
		assertThat(request.getGoodsSkus()).allSatisfy(item -> {
			assertThat(item.getTenantId()).isNull();
			assertThat(item.getSpuId()).isEqualTo("spu-1");
		});
	}

	@Test
	void saveGoodsRejectsEmptySkuList() {
		GoodsSpu request = product(null, List.of());

		assertThatThrownBy(() -> service.saveGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("SKU"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void saveGoodsRejectsMoreThanSixAttrTags() {
		GoodsSpu request = product(null, List.of());
		request.setAttrTags(attrTags(7));

		assertThatThrownBy(() -> service.saveGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(
					exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("最多配置 6 个"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void saveGoodsNormalizesAttrTagsBeforePersisting() {
		when(spuMapper.insert(any(GoodsSpu.class))).thenAnswer(invocation -> {
			GoodsSpu saved = invocation.getArgument(0);
			saved.setId("spu-attr");
			return 1;
		});
		when(skuMapper.insert(any(GoodsSku.class))).thenReturn(1);
		GoodsSpu request = product(null, List.of(sku(null, 20, 100, 60, 3, 0)));
		GoodsAttrTag location = attrTag("  地标  ", " 系列 ");
		location.setTone("purple");
		request.setAttrTags(List.of(location, new GoodsAttrTag()));

		assertThat(service.saveGoods(request)).isTrue();

		// 保存前归一化：去空白、丢弃空占位行、未知色调收敛为 gray、key 按展示顺序重排
		assertThat(request.getAttrTags()).singleElement().satisfies(saved -> {
			assertThat(saved.getKey()).isEqualTo("attr-1");
			assertThat(saved.getValue()).isEqualTo("地标");
			assertThat(saved.getLabel()).isEqualTo("系列");
			assertThat(saved.getTone()).isEqualTo("gray");
		});
	}

	@Test
	void saveGoodsRejectsDuplicateSpecificationCombination() {
		GoodsSku first = sku(null, 10, 10, 8, 1, 0);
		GoodsSku second = sku(null, 12, 12, 9, 1, 0);
		GoodsSku.Specs red = new GoodsSku.Specs("spec-color", "颜色", "red", "红色");
		first.setSpecsArr(List.of(red));
		second.setSpecsArr(List.of(red));

		assertThatThrownBy(() -> service.saveGoods(product(null, List.of(first, second))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("重复"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void saveGoodsRejectsTotalStockOverflow() {
		GoodsSku first = sku(null, 10, 10, 8, Integer.MAX_VALUE, 0);
		first.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "red", "红色")));
		GoodsSku second = sku(null, 12, 12, 9, 1, 0);
		second.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "blue", "蓝色")));

		assertThatThrownBy(() -> service.saveGoods(product(null, List.of(first, second))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("库存总量"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void updateGoodsRejectsSkuOwnedByAnotherProduct() {
		GoodsSpu existing = product("spu-1", List.of());
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(sku("sku-1", 10, 10, 8, 5, 2)));
		GoodsSpu request = product("spu-1", List.of(sku("sku-other", 10, 10, 8, 5, 1)));

		assertThatThrownBy(() -> service.updateGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("不属于当前商品"));
		verify(skuMapper, never()).updateById(any(GoodsSku.class));
	}

	@Test
	void updateGoodsRejectsStaleSkuVersion() {
		GoodsSpu existing = product("spu-1", List.of());
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(sku("sku-1", 10, 10, 8, 5, 3)));
		GoodsSpu request = product("spu-1", List.of(sku("sku-1", 10, 10, 8, 6, 2)));

		assertThatThrownBy(() -> service.updateGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("库存已变化"));
		verify(skuMapper, never()).updateById(any(GoodsSku.class));
	}

	@Test
	void updateGoodsAppliesStockDeltaInsteadOfOverwritingAggregateStock() {
		GoodsSpu existing = product("spu-1", List.of());
		existing.setStock(5);
		GoodsSku storedSku = sku("sku-1", 10, 10, 8, 5, 2);
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(storedSku));
		when(skuMapper.updateById(any(GoodsSku.class))).thenReturn(1);
		when(spuMapper.updateById(any(GoodsSpu.class))).thenReturn(1);
		when(spuMapper.update(any(GoodsSpu.class), any(Wrapper.class))).thenReturn(1);
		GoodsSpu request = product("spu-1", List.of(sku("sku-1", 12, 15, 9, 8, 2)));
		request.setStock(999);
		request.setTenantId("victim-tenant");

		assertThat(service.updateGoods(request)).isTrue();

		assertThat(request.getStock()).isNull();
		assertThat(request.getTenantId()).isNull();
		verify(spuMapper).update(any(GoodsSpu.class), any(Wrapper.class));
	}

	@Test
	void apiPageMasksCostPriceForApp() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		GoodsSpu spu = product("spu-1", List.of());
		spu.setSalesPrice(BigDecimal.valueOf(20));
		spu.setOriginalPrice(BigDecimal.valueOf(80));
		spu.setCostPrice(BigDecimal.valueOf(40));
		page.setRecords(List.of(spu));
		when(spuMapper.selectApiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);

		IPage<GoodsSpu> result = service.apiPage(page, new GoodsSpu());

		// C 端列表不下发成本价；售价/原价是展示所需，必须保留
		assertThat(result.getRecords()).hasSize(1);
		assertThat(result.getRecords().get(0).getCostPrice()).isNull();
		assertThat(result.getRecords().get(0).getSalesPrice()).isEqualByComparingTo("20");
		assertThat(result.getRecords().get(0).getOriginalPrice()).isEqualByComparingTo("80");
	}

	@Test
	void apiPageWithoutBargainRateKeepsLegacyOrdersAndPassesNullThreshold() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		page.setOrders(new ArrayList<>(List.of(OrderItem.asc("sales_price"), OrderItem.desc("create_time"))));
		when(spuMapper.selectApiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);

		service.apiPage(page, new GoodsSpu());

		// 存量路径：不传 bargainRate 时 asc/desc 原样保留（仍由分页插件追加 ORDER BY），阈值以 null 下传
		assertThat(page.orders()).extracting(OrderItem::getColumn).containsExactly("sales_price", "create_time");
		assertThat(page.orders()).extracting(OrderItem::isAsc).containsExactly(true, false);
		verify(spuMapper).selectApiPage(eq(page), any(GoodsSpu.class), isNull());
	}

	@Test
	void apiPageWithBargainRateClearsAscDescBeforeQuery() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		page.setOrders(new ArrayList<>(List.of(OrderItem.desc("sales_price"))));
		GoodsSpu query = new GoodsSpu();
		when(spuMapper.selectApiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);

		service.apiPage(page, query, new BigDecimal("1.15"));

		// 捡漏路径：固定排序由 XML 输出，asc/desc 必须被清空——既避免再拼一层排序列，
		// 也彻底关掉「用户传列名」的注入面（排序键只能是 XML 里写死的表达式）
		assertThat(page.orders()).isEmpty();
		verify(spuMapper).selectApiPage(page, query, new BigDecimal("1.15"));
	}

	@Test
	void apiPageRejectsBargainRateOutOfRange() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		// 下界开区间 1.00 不合法；上界 9.99 闭区间；负数/超大值一律业务错误（不落到 SQL、不返回 500）
		for (String illegal : List.of("0.5", "1.00", "-1", "9.991", "10")) {
			assertThatThrownBy(() -> service.apiPage(page, new GoodsSpu(), new BigDecimal(illegal)))
				.isInstanceOf(ArynBusinessException.class)
				.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("bargainRate")
					.contains("(1.00, 9.99]"));
		}
		verify(spuMapper, never()).selectApiPage(any(Page.class), any(GoodsSpu.class), any());
	}

	@Test
	void apiPageAcceptsBargainRateBoundaries() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		when(spuMapper.selectApiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);

		service.apiPage(page, new GoodsSpu(), new BigDecimal("1.01"));
		service.apiPage(page, new GoodsSpu(), new BigDecimal("9.99"));

		verify(spuMapper).selectApiPage(eq(page), any(GoodsSpu.class), eq(new BigDecimal("1.01")));
		verify(spuMapper).selectApiPage(eq(page), any(GoodsSpu.class), eq(new BigDecimal("9.99")));
	}

	@Test
	void recommendPageUsesContextCategoriesAndMasksCostPrice() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		page.setOrders(new ArrayList<>(List.of(OrderItem.desc("sales_price"))));
		when(spuMapper.selectBatchIds(any())).thenReturn(List.of(product("spu-1", List.of()),
				product("spu-2", List.of())));
		GoodsSpu recommended = product("spu-3", List.of());
		recommended.setCostPrice(BigDecimal.valueOf(40));
		page.setRecords(List.of(recommended));
		when(spuMapper.selectRecommendPage(any(Page.class), any(), any(), any())).thenReturn(page);

		IPage<GoodsSpu> result = service.recommendPage(page, "spu-1", List.of("spu-2"), List.of("spu-9"));

		// 当前商品与额外排除项合并；分类上下文来自 spuId/sourceSpuIds 对应商品
		verify(spuMapper).selectRecommendPage(page, List.of("spu-1", "spu-9"),
				List.of("category-2"), List.of("category-1"));
		// 固定排序由 XML 输出；推荐流无限滚动，不查总数
		assertThat(page.orders()).isEmpty();
		assertThat(page.searchCount()).isFalse();
		// C 端推荐列表不下发成本价
		assertThat(result.getRecords().get(0).getCostPrice()).isNull();
	}

	@Test
	void recommendPageWithoutContextAndGuestFallsBackToGlobalOrdering() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		when(spuMapper.selectRecommendPage(any(Page.class), any(), any(), any())).thenReturn(page);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(null);
			service.recommendPage(page, null, null, null);
		}

		// 未登录且无显式上下文：不读足迹、不查分类，仅保留全店销量/上架时间排序
		verify(footprintMapper, never()).selectList(any(Wrapper.class));
		verify(spuMapper, never()).selectBatchIds(any());
		verify(spuMapper).selectRecommendPage(eq(page), eq(List.of()), eq(List.of()), eq(List.of()));
	}

	@Test
	void recommendPageWithoutContextUsesRecentFootprintsForLoggedInUser() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		GoodsFootprint first = new GoodsFootprint()
			.setUserId("user-1")
			.setSpuId("spu-1")
			.setCreateTime(LocalDateTime.now());
		GoodsFootprint second = new GoodsFootprint()
			.setUserId("user-1")
			.setSpuId("spu-2")
			.setCreateTime(LocalDateTime.now().minusDays(1));
		when(footprintMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
		when(spuMapper.selectBatchIds(any())).thenReturn(List.of(product("spu-1", List.of()),
				product("spu-2", List.of())));
		when(spuMapper.selectRecommendPage(any(Page.class), any(), any(), any())).thenReturn(page);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(loginUser("user-1"));
			service.recommendPage(page, null, null, null);
		}

		// 个人中心无显式上下文：用近期浏览足迹的商品品类做个性化，不排除已浏览商品
		verify(spuMapper).selectBatchIds(any());
		verify(spuMapper).selectRecommendPage(eq(page), eq(List.of()),
				eq(List.of("category-2")), eq(List.of("category-1")));
	}

	@Test
	void getApiSpuByIdMasksCostPriceOnSpuAndSkus() {
		GoodsSpu spu = product("spu-1", List.of(sku("sku-1", 20, 80, 40, 3, 0)));
		spu.setCostPrice(BigDecimal.valueOf(40));
		when(spuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		GoodsSpu result = service.getApiSpuById("spu-1");

		assertThat(result.getCostPrice()).isNull();
		assertThat(result.getGoodsSkus()).allSatisfy(item -> {
			assertThat(item.getCostPrice()).isNull();
			assertThat(item.getSalesPrice()).isEqualByComparingTo("20");
			assertThat(item.getOriginalPrice()).isEqualByComparingTo("80");
		});
	}

	@Test
	void getApiSpuByIdRefreshesExistingFootprintInsteadOfInserting() {
		when(spuMapper.selectApiSpuById("spu-1")).thenReturn(product("spu-1", List.of()));
		GoodsFootprint existing = new GoodsFootprint()
			.setId("footprint-1")
			.setUserId("user-1")
			.setSpuId("spu-1")
			.setCreateTime(LocalDateTime.of(2026, 10, 1, 9, 0));
		when(footprintMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(loginUser("user-1"));
			service.getApiSpuById("spu-1");
		}

		ArgumentCaptor<GoodsFootprint> captor = ArgumentCaptor.forClass(GoodsFootprint.class);
		verify(footprintMapper).updateById(captor.capture());
		// 同一商品再次浏览：只刷新时间，不新增记录
		assertThat(captor.getValue().getCreateTime()).isAfter(LocalDateTime.of(2026, 10, 1, 9, 0));
		verify(footprintMapper, never()).insert(any(GoodsFootprint.class));
	}

	@Test
	void getApiSpuByIdInsertsFootprintOnFirstView() {
		when(spuMapper.selectApiSpuById("spu-2")).thenReturn(product("spu-2", List.of()));
		when(footprintMapper.selectOne(any(Wrapper.class))).thenReturn(null);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::getUser).thenReturn(loginUser("user-1"));
			service.getApiSpuById("spu-2");
		}

		ArgumentCaptor<GoodsFootprint> captor = ArgumentCaptor.forClass(GoodsFootprint.class);
		verify(footprintMapper).insert(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(captor.getValue().getSpuId()).isEqualTo("spu-2");
		verify(footprintMapper, never()).updateById(any(GoodsFootprint.class));
	}

	@Test
	void apiListByIdsReturnsOnlyPublishedGoodsAndMasksCostPrice() {
		GoodsSpu published = product("spu-1", List.of());
		published.setSalesPrice(BigDecimal.valueOf(20));
		published.setCostPrice(BigDecimal.valueOf(40));
		when(spuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(published));

		List<GoodsSpu> result = service.apiListByIds(List.of("spu-1"));

		assertThat(result.get(0).getCostPrice()).isNull();
		assertThat(result.get(0).getSalesPrice()).isEqualByComparingTo("20");
		// 仅上架商品的过滤条件从控制器收拢到了服务层，这里锁住，防止回归
		ArgumentCaptor<Wrapper<GoodsSpu>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(spuMapper).selectList(wrapperCaptor.capture());
		assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("status"));
	}

	@Test
	void escapeLikeKeywordEscapesWildcardsAndEscapeChar() {
		assertThat(GoodsSpuServiceImpl.escapeLikeKeyword("a%b_c!d")).isEqualTo("a!%b!_c!!d");
		// 反斜杠不是特殊字符（ESCAPE '!'），按字面量透传
		assertThat(GoodsSpuServiceImpl.escapeLikeKeyword("红富士\\苹果")).isEqualTo("红富士\\苹果");
		assertThat(GoodsSpuServiceImpl.escapeLikeKeyword("苹果")).isEqualTo("苹果");
	}

	@Test
	void suggestNamesFailsClosedWithoutTenant() {
		assertThat(service.suggestNames(null, "苹果")).isEmpty();
		assertThat(service.suggestNames("  ", "苹果")).isEmpty();
		verify(spuMapper, never()).selectSuggestNames(anyString(), anyString(), anyInt());
	}

	@Test
	void suggestNamesRejectsInvalidKeywordWithoutQuerying() {
		assertThat(service.suggestNames("t-1", null)).isEmpty();
		assertThat(service.suggestNames("t-1", "   ")).isEmpty();
		// 纯控制字符清洗后为空
		assertThat(service.suggestNames("t-1", "\u0001\t\n")).isEmpty();
		// 超长（>20）直接判空，不报错
		assertThat(service.suggestNames("t-1", "a".repeat(21))).isEmpty();
		verify(spuMapper, never()).selectSuggestNames(anyString(), anyString(), anyInt());
	}

	@Test
	void suggestNamesStripsControlCharsAndClampsLength() {
		when(spuMapper.selectSuggestNames(anyString(), anyString(), anyInt())).thenReturn(List.of());
		String twentyChars = "苹".repeat(20);

		service.suggestNames("t-1", " 苹\u0001果\t\n");
		service.suggestNames("t-1", twentyChars);

		verify(spuMapper).selectSuggestNames(eq("t-1"), eq("苹果"), anyInt());
		verify(spuMapper).selectSuggestNames(eq("t-1"), eq(twentyChars), anyInt());
	}

	@Test
	void suggestNamesEscapesWildcardsAndPassesTenantExplicitly() {
		when(spuMapper.selectSuggestNames(anyString(), anyString(), anyInt())).thenReturn(List.of());

		service.suggestNames("tenant-1", "苹果%_x!");

		verify(spuMapper).selectSuggestNames(eq("tenant-1"), eq("苹果!%!_x!!"), anyInt());
	}

	@Test
	void suggestNamesRanksPrefixFirstDedupesAndExcludesExactInput() {
		when(spuMapper.selectSuggestNames(anyString(), anyString(), anyInt()))
			.thenReturn(List.of("红富士苹果", "苹果醋", "苹果汁", "苹果醋", "苹果"));

		List<String> result = service.suggestNames("t-1", "苹果");

		// 前缀命中（苹果醋/苹果汁）优先于包含命中（红富士苹果）；
		// 重名去重；与输入完全相同的「苹果」不重复推荐
		assertThat(result).containsExactly("苹果醋", "苹果汁", "红富士苹果");
	}

	@Test
	void suggestNamesCapsAtTenWords() {
		List<String> candidates = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			candidates.add("苹果" + i);
		}
		when(spuMapper.selectSuggestNames(anyString(), anyString(), anyInt())).thenReturn(candidates);

		assertThat(service.suggestNames("t-1", "苹果")).hasSize(10);
	}

	@Test
	void apiPageEscapesNameWildcards() {
		Page<GoodsSpu> page = new Page<>(1, 10);
		when(spuMapper.selectApiPage(any(Page.class), any(GoodsSpu.class), any())).thenReturn(page);
		GoodsSpu query = new GoodsSpu();
		query.setName("50%off_1");

		service.apiPage(page, query);

		assertThat(query.getName()).isEqualTo("50!%off!_1");
	}

	@Test
	void catalogPageEscapesKeywordWildcards() {
		Page<GoodsCatalogSummaryVO> page = new Page<>(1, 10);
		when(spuMapper.selectCatalogPage(any(), anyString(), anyString(), anyString())).thenReturn(page);

		service.catalogPage("t-1", page, "苹果%", "1");

		verify(spuMapper).selectCatalogPage(any(), eq("t-1"), eq("苹果!%"), eq("1"));
	}

	private static ArynUser loginUser(String userId) {
		ArynUser user = new ArynUser();
		user.setUserId(userId);
		return user;
	}

	private GoodsAttrTag attrTag(String value, String label) {
		GoodsAttrTag tag = new GoodsAttrTag();
		tag.setValue(value);
		tag.setLabel(label);
		return tag;
	}

	private List<GoodsAttrTag> attrTags(int count) {
		List<GoodsAttrTag> tags = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			tags.add(attrTag("值" + i, "名" + i));
		}
		return tags;
	}

	private GoodsSpu product(String id, List<GoodsSku> skus) {
		GoodsSpu product = new GoodsSpu();
		product.setId(id);
		product.setName("测试商品");
		product.setSpuUrls(new String[] { "image.jpg" });
		product.setStatus("1");
		product.setEnableSpecs(skus.size() > 1 ? "1" : "0");
		product.setCategoryFirstId("category-1");
		product.setCategorySecondId("category-2");
		product.setFreightType("0");
		product.setGoodsSkus(skus);
		return product;
	}

	private GoodsSku sku(String id, int salesPrice, int originalPrice, int costPrice, int stock, int version) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSalesPrice(BigDecimal.valueOf(salesPrice));
		sku.setOriginalPrice(BigDecimal.valueOf(originalPrice));
		sku.setCostPrice(BigDecimal.valueOf(costPrice));
		sku.setStock(stock);
		sku.setStatus("0");
		sku.setVersion(version);
		return sku;
	}

	private static final class TestGoodsSpuService extends GoodsSpuServiceImpl {

		private TestGoodsSpuService(GoodsSkuMapper goodsSkuMapper, GoodsCollectMapper goodsCollectMapper,
				GoodsFootprintMapper goodsFootprintMapper, GoodsCategoryMapper goodsCategoryMapper,
				GoodsSpuMapper goodsSpuMapper) {
			super(goodsSkuMapper, goodsCollectMapper, goodsFootprintMapper, goodsCategoryMapper);
			this.baseMapper = goodsSpuMapper;
		}

	}

}
