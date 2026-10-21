package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService;
import com.aryn.cloud.promotion.api.vo.AppNewbieFloorVO;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.NewbieActivityServiceImpl;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 新人专享「指定 SKU 新人价」查询契约测试。
 *
 * <p>接口 {@code GET /app/newbie/prices} 供商详 / 购物车使用，语义核心有三条，本类逐条守住：
 * <ol>
 *   <li><b>免登可用</b>：未登录只下发活动配置价、资格字段为 null，且绝不能为了判定资格去调
 *       远程买家画像（否则免登请求会被远程调用拖垮，且违反「不收窄 C 端读接口鉴权」的约定）；</li>
 *   <li><b>资格 fail-closed</b>：远程判定异常时按「不可享」返回并带 ERROR 原因，
 *       绝不把异常抛给展示端——展示失败不该阻断页面渲染，真正拦截在下单链路；</li>
 *   <li><b>降级不阻断</b>：商品远程查询异常 / 规则损坏 / 无交集 SKU 时返回 null，
 *       页面只是少了价格标注，不能 500。</li>
 * </ol>
 *
 * <p>另守住请求上限 {@code MAX_PRICE_QUERY_SKUS}：批量查价只取前 200 个 SKU 查商品，
 * 防止请求体放大成慢查询（超出部分静默忽略，只影响价格标注、不影响下单）。
 */
class NewbieActivityServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private PromotionActivityMapper activityMapper;

	private RemoteGoodsSkuService goodsSkuService;

	private RemoteBuyerProfileService buyerProfileService;

	private NewbieActivityServiceImpl service;

	@BeforeAll
	static void initLambdaCache() {
		// 纯 Mockito 环境没有 MyBatis 启动流程：手动初始化 lambda 包装所需的实体列缓存，
		// 否则条件构造器在解析 SFunction 列名时会抛 "can not find lambda cache"
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				PromotionActivity.class);
	}

	@BeforeEach
	void setUp() {
		activityMapper = mock(PromotionActivityMapper.class);
		goodsSkuService = mock(RemoteGoodsSkuService.class);
		buyerProfileService = mock(RemoteBuyerProfileService.class);
		// 构造器只接收两个 final 依赖；资格回调是 @DubboReference 非 final 字段（boot 启动约束），
		// 只能用反射注入，与 PromotionEngineTest 的注入方式一致
		service = new NewbieActivityServiceImpl(activityMapper, goodsSkuService);
		ReflectionTestUtils.setField(service, "remoteBuyerProfileService", buyerProfileService);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	// ==== 用例 1：空入参短路 ====

	@Test
	@DisplayName("skuIds 为空/null/全空白时直接返回 null，不查库")
	void emptySkuIdsReturnsNullWithoutQuery() {
		assertNull(service.getSkuPrices(null, USER));
		assertNull(service.getSkuPrices(List.of(), USER));
		// 前端可能带 "skuIds=" 的空串：过滤后无有效 SKU，同样不发查询
		assertNull(service.getSkuPrices(List.of("", "   "), USER));

		verify(activityMapper, never()).selectList(any());
		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	// ==== 用例 2：请求 SKU 全未命中活动配置 ====

	@Test
	@DisplayName("请求 SKU 未命中活动配置时返回 null")
	void skusNotCoveredByActivityReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", newbieRules(30, 1, "sku-9", "8.8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		assertNull(service.getSkuPrices(List.of("sku-1"), USER));
	}

	// ==== 用例 3：正常命中 ====

	@Test
	@DisplayName("命中时 items 只含「请求 ∩ 活动配置」的 SKU，且活动基础字段回填正确")
	void hitReturnsIntersectionAndFillsActivityBase() {
		PromotionActivity activity = activity("act-1", newbieRules(30, 1, "sku-1", "9.9", "sku-2", "8"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-1", "29.9"), sku("sku-2", "spu-2", "20"), sku("sku-3", "spu-3", "15")));

		AppNewbiePriceVO result = service.getSkuPrices(List.of("sku-1", "sku-3"), null);

		assertNotNull(result);
		assertEquals("act-1", result.getActivityId());
		assertEquals(activity.getActivityName(), result.getActivityName());
		assertEquals(30, result.getRegisterWithinDays());
		// rules.limitPerUser 显式配置为 1
		assertEquals(1, result.getLimitPerUser());
		assertEquals(activity.getEndTime(), result.getEndTime());
		// 未登录：资格字段不下发，展示端按可享新人价处理
		assertNull(result.getEligible());
		assertNull(result.getIneligibleReason());
		// 只下发请求与活动配置的交集：sku-2 在活动里但没被请求、sku-3 被请求但不在活动里，都不出现
		assertEquals(1, result.getItems().size());
		AppNewbiePriceVO.NewbieSkuPriceVO item = result.getItems().get(0);
		assertEquals("sku-1", item.getSkuId());
		assertEquals("spu-1", item.getSpuId());
		assertEquals(0, item.getNewbiePrice().compareTo(new BigDecimal("9.9")));
		// 划线价取商品库当前售价，而不是活动价
		assertEquals(0, item.getOriginalPrice().compareTo(new BigDecimal("29.9")));
	}

	// ==== 用例 4：未登录不判资格 ====

	@Test
	@DisplayName("未登录（userId 为 null/空）时 eligible 为 null 且不调用远程资格服务")
	void anonymousSkipsEligibilityCheck() {
		PromotionActivity activity = activity("act-1", newbieRules(30, 1, "sku-1", "9.9"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		AppNewbiePriceVO anonymous = service.getSkuPrices(List.of("sku-1"), null);
		assertNotNull(anonymous);
		assertNull(anonymous.getEligible());
		assertNull(anonymous.getIneligibleReason());

		AppNewbiePriceVO blankUser = service.getSkuPrices(List.of("sku-1"), "");
		assertNotNull(blankUser);
		assertNull(blankUser.getEligible());
		assertNull(blankUser.getIneligibleReason());

		verify(buyerProfileService, never()).checkNewbieEligibility(any(), any(), any());
	}

	// ==== 用例 5：登录且具备资格 ====

	@Test
	@DisplayName("已登录且资格通过时 eligible=true")
	void loggedInEligibleReturnsTrue() {
		PromotionActivity activity = activity("act-1", newbieRules(30, 1, "sku-1", "9.9"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(buyerProfileService.checkNewbieEligibility(any(), eq(USER), eq(30)))
			.thenReturn(eligibility(true, null));

		AppNewbiePriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertTrue(result.getEligible());
		assertNull(result.getIneligibleReason());
		// 注册时效取自活动 rules，而不是硬编码：远程判定口径必须与活动配置一致
		verify(buyerProfileService).checkNewbieEligibility(any(), eq(USER), eq(30));
	}

	// ==== 用例 6：登录但不具备资格 ====

	@Test
	@DisplayName("已登录但资格不通过时 eligible=false 且原因透传")
	void loggedInIneligiblePassesReasonThrough() {
		PromotionActivity activity = activity("act-1", newbieRules(30, 1, "sku-1", "9.9"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(buyerProfileService.checkNewbieEligibility(any(), eq(USER), any()))
			.thenReturn(eligibility(false, "ALREADY_PAID"));

		AppNewbiePriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertFalse(result.getEligible());
		// 原因必须原样透传：展示端据此显示「已下过单」还是「注册超时」
		assertEquals("ALREADY_PAID", result.getIneligibleReason());
	}

	// ==== 用例 7：资格查询异常 fail-closed ====

	@Test
	@DisplayName("资格查询抛异常时 fail-closed：eligible=false、原因 ERROR，且不抛出")
	void eligibilityErrorFailsClosedWithoutThrowing() {
		PromotionActivity activity = activity("act-1", newbieRules(30, 1, "sku-1", "9.9"));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(buyerProfileService.checkNewbieEligibility(any(), any(), any()))
			.thenThrow(new RuntimeException("dubbo timeout"));

		AppNewbiePriceVO result = assertDoesNotThrow(() -> service.getSkuPrices(List.of("sku-1"), USER));

		assertNotNull(result);
		// 远程失败不能当成「可享」下发（否则页面标新人价、结算按原价，价差观感更差）
		assertFalse(result.getEligible());
		assertEquals("ERROR", result.getIneligibleReason());
		assertEquals(1, result.getItems().size());
	}

	// ==== 用例 8：商品远程查询异常降级 ====

	@Test
	@DisplayName("商品远程查询抛异常时返回 null（降级剔除，不阻断）")
	void goodsQueryErrorReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", newbieRules(30, 1, "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenThrow(new RuntimeException("product service down"));

		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));
	}

	// ==== 用例 9：单次查询 SKU 上限 ====

	@Test
	@DisplayName("超过 MAX_PRICE_QUERY_SKUS 时只取前 200 个 SKU 查商品")
	void capsGoodsQueryAtMaxPriceQuerySkus() {
		List<String> requested = IntStream.range(0, 300).mapToObj(i -> "sku-" + i).toList();
		List<GoodsSku> goods = new ArrayList<>();
		StringBuilder skuRules = new StringBuilder();
		for (int i = 0; i < requested.size(); i++) {
			if (i > 0) {
				skuRules.append(',');
			}
			// 活动配置覆盖全部 300 个：上限只由请求侧约束，不依赖活动配置
			skuRules.append("{\"skuId\":\"").append(requested.get(i)).append("\",\"price\":").append(9.9).append('}');
			if (i < 200) {
				goods.add(sku(requested.get(i), "spu-" + i, "29.9"));
			}
		}
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "{\"registerWithinDays\":30,\"limitPerUser\":1,\"skus\":[" + skuRules + "]}")));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(goods);

		AppNewbiePriceVO result = service.getSkuPrices(requested, USER);

		assertNotNull(result);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuService).getBySkuIds(captor.capture());
		// 只查前 200 个，且保持请求顺序（LinkedHashSet 去重保序）
		assertEquals(200, captor.getValue().size());
		assertEquals(requested.subList(0, 200), captor.getValue());
		// 第 201 个之后不下发价格标注（静默忽略，不影响下单）
		assertEquals(200, result.getItems().size());
	}

	// ==== 用例 10：多活动并存时的降级选择 ====

	@Test
	@DisplayName("高优先级活动未覆盖请求 SKU 时继续尝试低优先级活动")
	void fallsThroughToLowerPriorityActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				// mapper 按 priority 降序返回：先到的是高优先级活动，但它只配了 sku-9
				activity("act-high", newbieRules(30, 1, "sku-9", "1")),
				activity("act-low", newbieRules(30, 1, "sku-1", "8.8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		AppNewbiePriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertEquals("act-low", result.getActivityId());
		assertEquals(0, result.getItems().get(0).getNewbiePrice().compareTo(new BigDecimal("8.8")));
	}

	// ==== 用例 11：规则损坏 / 无 skus ====

	@Test
	@DisplayName("rules 损坏或缺少 skus 时返回 null 且不抛异常")
	void brokenOrEmptyRulesSkipped() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-broken", "{broken json")));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));

		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-empty", "{\"registerWithinDays\":30,\"limitPerUser\":1}")));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));

		// rules 为 null（历史数据可能没回填）同样只跳过，不阻断
		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-null", null)));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));
	}

	// ==== 用例 12：两个出口共用同一活动选择口径 ====

	@Test
	@DisplayName("楼层与指定 SKU 查价共用同一活动选择口径：都跳过不可用活动、都取首个可用活动")
	void floorAndPricesShareActivitySelection() {
		// 优先级最高的活动规则损坏（不可用），次高的活动可用：两个出口都必须落到 act-2
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-broken", "{broken json"),
				activity("act-2", newbieRules(30, 1, "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		AppNewbieFloorVO floor = service.getCurrentFloor();
		AppNewbiePriceVO price = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(floor);
		assertNotNull(price);
		assertEquals("act-2", floor.getActivityId());
		assertEquals("act-2", price.getActivityId());
		// 同一 mapper 返回下两者选中的是同一个活动，价格口径也一致
		assertEquals(0, floor.getGoodsList().get(0).getNewbiePrice()
			.compareTo(price.getItems().get(0).getNewbiePrice()));

		// 全部活动都不可用时：两个出口都返回 null（同一「无可用活动」语义）
		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-broken", "{broken json")));
		assertNull(service.getCurrentFloor());
		assertNull(service.getSkuPrices(List.of("sku-1"), USER));
	}

	// ==== 测试数据构造 ====

	/**
	 * 构造新人价活动。
	 *
	 * <p>注意：{@code activeActivities()} 的过滤条件（类型 9、已发布、时间窗内、优先级降序）
	 * 由 SQL 承担，mapper 在单测里是 mock，所以这里只需保证 activityType/status 与生产一致，
	 * 列表顺序即「优先级降序」。
	 */
	private PromotionActivity activity(String id, String rules) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setTenantId(TENANT);
		activity.setActivityName("新人专享-" + id);
		activity.setActivityType(PromotionActivity.TYPE_NEWBIE_PRICE);
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setStartTime(LocalDateTime.now().minusDays(1));
		activity.setEndTime(LocalDateTime.now().plusDays(3));
		activity.setRules(rules);
		return activity;
	}

	/**
	 * rules JSON（与发布校验写入的结构一致）：registerWithinDays + limitPerUser + skus。
	 *
	 * @param skuPricePairs skuId、price 交替传入，如 ("sku-1", "9.9")
	 */
	private String newbieRules(int registerWithinDays, int limitPerUser, String... skuPricePairs) {
		StringBuilder skus = new StringBuilder();
		for (int i = 0; i < skuPricePairs.length; i += 2) {
			if (i > 0) {
				skus.append(',');
			}
			skus.append("{\"skuId\":\"")
				.append(skuPricePairs[i])
				.append("\",\"price\":")
				.append(skuPricePairs[i + 1])
				.append('}');
		}
		return "{\"registerWithinDays\":" + registerWithinDays + ",\"limitPerUser\":" + limitPerUser
				+ ",\"skus\":[" + skus + "]}";
	}

	private GoodsSku sku(String id, String spuId, String salesPrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		return sku;
	}

	private NewbieEligibilityVO eligibility(boolean eligible, String reason) {
		NewbieEligibilityVO vo = new NewbieEligibilityVO();
		vo.setEligible(eligible);
		vo.setReason(reason);
		return vo;
	}

}
