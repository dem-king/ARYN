package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.MemberActivityGoodsVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.MemberActivityGoodsServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员专区自动数据源（{@code GET /app/member/activity-goods}）契约测试。
 *
 * <p>守住：
 * <ol>
 *   <li><b>活动选择</b>：不传活动ID取当前进行中的会员价活动；传 ID 时只校验存在 + type=10，
 *       <b>不校验时间窗</b>（允许提前装修/活动结束后保留楼层）；</li>
 *   <li><b>去重保序</b>：按活动 rules.skus 配置顺序回填 spuId，同一 SPU 多规格只保留首次出现位置；</li>
 *   <li><b>上限保护</b>：SKU 100 / SPU 50 静默截断；</li>
 *   <li><b>降级不阻断</b>：无活动/无商品/规则损坏/商品 RPC 异常一律返回 null，不抛异常不分页失败。</li>
 * </ol>
 */
class MemberActivityGoodsServiceTest {

	private static final String TENANT = "tenant-1";

	private PromotionActivityMapper activityMapper;

	private RemoteGoodsSkuService goodsSkuService;

	private MemberActivityGoodsServiceImpl service;

	@BeforeAll
	static void initLambdaCache() {
		// 纯 Mockito 环境没有 MyBatis 启动流程：手动初始化 lambda 包装所需的实体列缓存
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				PromotionActivity.class);
	}

	@BeforeEach
	void setUp() {
		activityMapper = mock(PromotionActivityMapper.class);
		goodsSkuService = mock(RemoteGoodsSkuService.class);
		// 构造器只接收 final 的 mapper；商品远程接口是 @DubboReference 非 final 字段（boot 启动约束），反射注入
		service = new MemberActivityGoodsServiceImpl(activityMapper);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", goodsSkuService);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	// ==== 用例 1：不传 activityId 取当前进行中的会员价活动 ====

	@Test
	@DisplayName("不传 activityId：取当前进行中活动，返回活动元信息与按配置顺序的 SPU 列表")
	void picksCurrentActiveActivity() {
		PromotionActivity activity = activity("act-1", "会员专区活动", memberRules("sku-1", "sku-2"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-1", "29.9"), sku("sku-2", "spu-2", "19.9")));

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		assertEquals("act-1", result.getActivityId());
		assertEquals("会员专区活动", result.getActivityName());
		assertEquals(activity.getEndTime(), result.getEndTime());
		assertEquals(List.of("spu-1", "spu-2"), result.getSpuIds());
		verify(activityMapper, never()).selectOne(any());
	}

	@Test
	@DisplayName("不传 activityId 且无进行中活动：返回 null")
	void returnsNullWhenNoActiveActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of());

		assertNull(service.getActivityGoods(""));
		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	// ==== 用例 2：传 activityId 时只校验存在 + type=10，不校验时间窗 ====

	@Test
	@DisplayName("传 activityId：已结束/草稿活动也返回（允许提前装修与活动结束后保留楼层）")
	void specifiedActivityIgnoresTimeWindow() {
		PromotionActivity expired = activity("act-2", "已结束会员价", memberRules("sku-1"));
		expired.setStatus(PromotionActivity.STATUS_DRAFT);
		expired.setStartTime(LocalDateTime.now().minusDays(30));
		expired.setEndTime(LocalDateTime.now().minusDays(10));
		when(activityMapper.selectOne(any())).thenReturn(expired);
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		MemberActivityGoodsVO result = service.getActivityGoods("act-2");

		assertNotNull(result);
		assertEquals("act-2", result.getActivityId());
		assertEquals(List.of("spu-1"), result.getSpuIds());
		verify(activityMapper, never()).selectList(any());
	}

	@Test
	@DisplayName("传 activityId 但活动不存在：返回 null")
	void specifiedActivityNotFoundReturnsNull() {
		when(activityMapper.selectOne(any())).thenReturn(null);

		assertNull(service.getActivityGoods("act-missing"));
	}

	@Test
	@DisplayName("传 activityId 但不是会员价类型：返回 null（不返回其它类型活动的商品）")
	void specifiedNonMemberPriceActivityReturnsNull() {
		PromotionActivity ladder = activity("act-4", "阶梯价活动", memberRules("sku-1"));
		ladder.setActivityType(PromotionActivity.TYPE_LADDER_PRICE);
		when(activityMapper.selectOne(any())).thenReturn(ladder);

		assertNull(service.getActivityGoods("act-4"));
		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	// ==== 用例 3：SPU 去重保序 ====

	@Test
	@DisplayName("同一 SPU 多个 SKU 只保留首次出现位置（活动 SKU 配置顺序即展示顺序）")
	void dedupesSpuPreservingConfiguredOrder() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "会员专区", memberRules("sku-1", "sku-2", "sku-3"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-a", "29.9"), sku("sku-2", "spu-b", "19.9"), sku("sku-3", "spu-a", "39.9")));

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		assertEquals(List.of("spu-a", "spu-b"), result.getSpuIds(), "同 SPU 去重且保留首次出现位置");
	}

	@Test
	@DisplayName("商品下架/删除导致部分 SKU 查不到时跳过该 SKU，其余照常返回")
	void skipsMissingGoods() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "会员专区", memberRules("sku-1", "sku-2", "sku-3"))));
		// sku-2 在商品库不存在
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-a", "29.9"), sku("sku-3", "spu-c", "39.9")));

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		assertEquals(List.of("spu-a", "spu-c"), result.getSpuIds());
	}

	@Test
	@DisplayName("全部 SKU 都查不到商品：返回 null（专区不渲染空楼层）")
	void allGoodsMissingReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "会员专区", memberRules("sku-1"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of());

		assertNull(service.getActivityGoods(null));
	}

	// ==== 用例 4：上限保护 ====

	@Test
	@DisplayName("活动配置 SKU 超过 100：只解析前 100 个（截断不报错）")
	void capsConfiguredSkusAtHundred() {
		StringBuilder pairs = new StringBuilder();
		for (int i = 0; i < 150; i++) {
			if (i > 0) {
				pairs.append(',');
			}
			pairs.append("sku-").append(i);
		}
		when(activityMapper.selectList(any()))
			.thenReturn(List.of(activity("act-1", "会员专区", memberRules(pairs.toString().split(",")))));
		when(goodsSkuService.getBySkuIds(any())).thenAnswer(invocation -> {
			List<String> ids = invocation.getArgument(0);
			List<GoodsSku> goods = new ArrayList<>();
			for (String id : ids) {
				goods.add(sku(id, "spu-" + id.substring("sku-".length()), "29.9"));
			}
			return goods;
		});

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuService).getBySkuIds(captor.capture());
		assertEquals(100, captor.getValue().size(), "单次最多解析 100 个活动 SKU");
	}

	@Test
	@DisplayName("命中的 SPU 超过 50：只返回前 50 个（截断不报错）")
	void capsSpusAtFifty() {
		StringBuilder pairs = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			if (i > 0) {
				pairs.append(',');
			}
			pairs.append("sku-").append(i);
		}
		when(activityMapper.selectList(any()))
			.thenReturn(List.of(activity("act-1", "会员专区", memberRules(pairs.toString().split(",")))));
		// sku-i → spu-i：100 个不同 SPU，超出 SPU 上限
		when(goodsSkuService.getBySkuIds(any())).thenAnswer(invocation -> {
			List<String> ids = invocation.getArgument(0);
			List<GoodsSku> goods = new ArrayList<>();
			for (String id : ids) {
				goods.add(sku(id, "spu-" + id.substring("sku-".length()), "29.9"));
			}
			return goods;
		});

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		assertEquals(50, result.getSpuIds().size(), "单次最多返回 50 个 SPU");
		assertEquals("spu-0", result.getSpuIds().get(0));
	}

	// ==== 用例 5：降级与边界 ====

	@Test
	@DisplayName("商品 RPC 异常：返回 null 且不抛异常（不 500）")
	void goodsRpcErrorReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "会员专区", memberRules("sku-1"))));
		when(goodsSkuService.getBySkuIds(any())).thenThrow(new RuntimeException("product service down"));

		assertDoesNotThrow(() -> assertNull(service.getActivityGoods(null)));
	}

	@Test
	@DisplayName("活动 rules 损坏或没有 skus：返回 null 且不查商品")
	void brokenOrEmptyRulesReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-broken", "损坏", "{broken json")));
		assertDoesNotThrow(() -> assertNull(service.getActivityGoods(null)));

		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-empty", "无商品", "{\"minLevelId\":\"lv-1\"}")));
		assertDoesNotThrow(() -> assertNull(service.getActivityGoods(null)));

		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	@Test
	@DisplayName("商品存在但 spuId 为空：跳过该 SKU")
	void skipsGoodsWithoutSpuId() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "会员专区", memberRules("sku-1", "sku-2"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", null, "29.9"), sku("sku-2", "spu-b", "19.9")));

		MemberActivityGoodsVO result = service.getActivityGoods(null);

		assertNotNull(result);
		assertEquals(List.of("spu-b"), result.getSpuIds());
	}

	// ==== 测试数据构造 ====

	/** 构造已发布、时间窗覆盖当前时刻的会员价活动。 */
	private PromotionActivity activity(String id, String name, String rules) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setTenantId(TENANT);
		activity.setActivityName(name);
		activity.setActivityType(PromotionActivity.TYPE_MEMBER_PRICE);
		activity.setScopeType("2");
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setStartTime(LocalDateTime.now().minusDays(1));
		activity.setEndTime(LocalDateTime.now().plusDays(3));
		activity.setRules(rules);
		return activity;
	}

	/** rules JSON（与发布校验写入结构一致）：minLevelId + skus[{skuId,unitPrice}]。 */
	private String memberRules(String... skuIds) {
		StringBuilder skus = new StringBuilder();
		for (int i = 0; i < skuIds.length; i++) {
			if (i > 0) {
				skus.append(',');
			}
			skus.append("{\"skuId\":\"").append(skuIds[i]).append("\",\"unitPrice\":9.9}");
		}
		return "{\"minLevelId\":\"lv-1\",\"skus\":[" + skus + "]}";
	}

	private GoodsSku sku(String id, String spuId, String salesPrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		return sku;
	}

}
