package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.vo.AppDiscountVO;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.service.IDiscountGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量折扣价（商详 / 购物车免登查价）的价格口径与性能约束。
 *
 * <p>背景：限时折扣会场按活动展示折后价（SKU 10740000000000000355 售价 29.90、8 折 23.92），
 * 商详 / 购物车 / 加购弹层却只拿得到 SKU 售价，前后不一致。新增的批量接口必须与单 SKU 接口
 * {@code getGoodsDiscountInfo} 同一口径：基准价 = salesPrice、全场活动直接命中、指定商品活动需
 * 命中 discount_goods、取折后价最低的活动、折后价不低于原价不返回。
 *
 * <p>同时钉死性能约束：一次进行中活动查询 + 一次 discount_goods 批量查询 + 一次商品批量查询，
 * 任何「按 SKU 循环调单 SKU 接口」的 N+1 写法都会让下面 verify 的次数断言失败。
 */
class DiscountActivityPriceBatchTest {

	private IDiscountGoodsService discountGoodsService;
	private DiscountActivityMapper activityMapper;
	private RemoteGoodsSkuService remoteGoodsSkuService;
	private DiscountActivityServiceImpl service;

	@BeforeEach
	void setUp() {
		discountGoodsService = mock(IDiscountGoodsService.class);
		activityMapper = mock(DiscountActivityMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		service = new DiscountActivityServiceImpl(discountGoodsService, remoteGoodsSkuService);
		ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
	}

	@Test
	@DisplayName("命中多个活动时取折后价最低的一个")
	void picksLowestPriceAmongMatchedActivities() {
		stubActiveActivities(
				activity("act-80", 1, 1, "0.80"),
				activity("act-70", 1, 1, "0.70"));
		stubRemoteSkus(sku("sku-1", "spu-1", "29.90"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1"));

		assertThat(result).hasSize(1);
		AppDiscountVO vo = result.get(0);
		assertThat(vo.getActivityId()).isEqualTo("act-70");
		assertThat(vo.getActivityName()).isEqualTo("活动-act-70");
		assertThat(vo.getSkuId()).isEqualTo("sku-1");
		assertThat(vo.getSpuId()).isEqualTo("spu-1");
		assertThat(vo.getGoodsName()).isEqualTo("商品-spu-1");
		// 折扣基准是 SKU 销售价（salesPrice），不是划线价 originalPrice
		assertThat(vo.getOriginalPrice()).isEqualByComparingTo("29.90");
		assertThat(vo.getDiscountPrice()).isEqualByComparingTo("20.93");

		// 性能约束：活动 1 次、商品 1 次；全场活动不挂 discount_goods，无需查关联表
		verify(activityMapper, times(1)).selectList(any());
		verify(remoteGoodsSkuService, times(1)).getBySkuIds(anyList());
		verify(discountGoodsService, never()).list(any(Wrapper.class));
	}

	@Test
	@DisplayName("全场活动与指定商品活动混合时逐 SKU 取各自最优，且关联表只查一次")
	void combinesFullScopeAndSkuScopedActivities() {
		stubActiveActivities(
				activity("act-full", 1, 1, "0.80"),
				activity("act-scoped", 2, 1, "0.50"));
		stubScopedGoods(row("act-scoped", "sku-2"));
		stubRemoteSkus(
				sku("sku-1", "spu-1", "29.90"),
				sku("sku-2", "spu-2", "100.00"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1", "sku-2"));

		assertThat(result).hasSize(2);
		Map<String, AppDiscountVO> bySku = result.stream()
				.collect(Collectors.toMap(AppDiscountVO::getSkuId, vo -> vo));
		// sku-1 只命中全场 8 折；sku-2 命中指定商品 5 折（比全场更低）
		assertThat(bySku.get("sku-1").getActivityId()).isEqualTo("act-full");
		assertThat(bySku.get("sku-1").getDiscountPrice()).isEqualByComparingTo("23.92");
		assertThat(bySku.get("sku-2").getActivityId()).isEqualTo("act-scoped");
		assertThat(bySku.get("sku-2").getDiscountPrice()).isEqualByComparingTo("50.00");

		// N+1 守护：不论几个活动 / 几个 SKU，三类查询各只发生一次
		verify(activityMapper, times(1)).selectList(any());
		verify(discountGoodsService, times(1)).list(any(Wrapper.class));
		verify(remoteGoodsSkuService, times(1)).getBySkuIds(anyList());
	}

	@Test
	@DisplayName("无任何活动命中时不返回，也不打远程商品库")
	void returnsEmptyWhenNoActivityMatched() {
		stubActiveActivities(activity("act-scoped", 2, 1, "0.50"));
		// 关联表里只有别的 SKU：本批请求不命中
		stubScopedGoods(row("act-scoped", "sku-other"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1"));

		assertThat(result).isEmpty();
		verify(remoteGoodsSkuService, never()).getBySkuIds(anyList());
	}

	@Test
	@DisplayName("折后价不低于原价（含 100% 折扣、固定价等于原价）时不返回")
	void discardsDiscountNotLowerThanOriginalPrice() {
		// 打 10 折与原价持平；固定价恰好等于原价
		stubActiveActivities(
				activity("act-equal", 1, 1, "1.00"),
				activity("act-fixed", 1, 3, "29.90"));
		stubRemoteSkus(sku("sku-1", "spu-1", "29.90"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1"));

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("活动折扣类型/折扣值为空（非法配置）时跳过，不抛异常也不返回")
	void skipsInvalidActivityConfig() {
		stubActiveActivities(
				activity("act-null-type", 1, null, "0.80"),
				activity("act-null-value", 1, 1, null));
		stubRemoteSkus(sku("sku-1", "spu-1", "29.90"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1"));

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("请求入参去重、剔除空值，远程商品库只查一次且按请求顺序")
	void deduplicatesAndIgnoresBlankSkuIds() {
		stubActiveActivities(activity("act-80", 1, 1, "0.80"));
		stubRemoteSkus(
				sku("sku-1", "spu-1", "29.90"),
				sku("sku-2", "spu-2", "100.00"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(
				Arrays.asList("sku-1", "", "sku-1", "   ", "sku-2"));

		assertThat(result).extracting(AppDiscountVO::getSkuId).containsExactly("sku-1", "sku-2");
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(remoteGoodsSkuService).getBySkuIds(captor.capture());
		assertThat(captor.getValue()).containsExactly("sku-1", "sku-2");
	}

	@Test
	@DisplayName("空入参 / 全空值入参直接返回空列表，不查库不打远程")
	void returnsEmptyForEmptyOrBlankInput() {
		assertThat(service.getGoodsDiscountPrices(null)).isEmpty();
		assertThat(service.getGoodsDiscountPrices(List.of())).isEmpty();
		assertThat(service.getGoodsDiscountPrices(Arrays.asList("", "  "))).isEmpty();

		verify(activityMapper, never()).selectList(any());
		verify(remoteGoodsSkuService, never()).getBySkuIds(anyList());
		verify(discountGoodsService, never()).list(any(Wrapper.class));
	}

	@Test
	@DisplayName("商品库查不到或无销售价的 SKU 不返回（由页面回落原价）")
	void discardsSkuMissingFromGoodsLibrary() {
		stubActiveActivities(activity("act-80", 1, 1, "0.80"));
		// sku-2 不在商品库返回结果里
		stubRemoteSkus(sku("sku-1", "spu-1", "29.90"));

		List<AppDiscountVO> result = service.getGoodsDiscountPrices(List.of("sku-1", "sku-2"));

		assertThat(result).extracting(AppDiscountVO::getSkuId).containsExactly("sku-1");
	}

	// ==================== 测试数据构造 ====================

	private void stubActiveActivities(DiscountActivity... activities) {
		when(activityMapper.selectList(any())).thenReturn(Arrays.asList(activities));
	}

	private void stubRemoteSkus(GoodsSku... skus) {
		when(remoteGoodsSkuService.getBySkuIds(anyList())).thenReturn(Arrays.asList(skus));
	}

	private void stubScopedGoods(DiscountGoods... rows) {
		when(discountGoodsService.list(any(Wrapper.class))).thenReturn(Arrays.asList(rows));
	}

	private DiscountActivity activity(String id, Integer scope, Integer discountType, String discountValue) {
		DiscountActivity activity = new DiscountActivity();
		activity.setId(id);
		activity.setActivityName("活动-" + id);
		activity.setScope(scope);
		activity.setDiscountType(discountType);
		activity.setDiscountValue(discountValue == null ? null : new BigDecimal(discountValue));
		activity.setStatus(1);
		activity.setStartTime(LocalDateTime.now().minusDays(1));
		activity.setEndTime(LocalDateTime.now().plusDays(1));
		return activity;
	}

	private DiscountGoods row(String activityId, String skuId) {
		DiscountGoods goods = new DiscountGoods();
		goods.setActivityId(activityId);
		goods.setSkuId(skuId);
		return goods;
	}

	private GoodsSku sku(String skuId, String spuId, String salesPrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSpuId(spuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		GoodsSpu spu = new GoodsSpu();
		spu.setName("商品-" + spuId);
		sku.setGoodsSpu(spu);
		return sku;
	}

}
