package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.AppMemberPriceVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.MemberPriceActivityServiceImpl;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteMemberLevelService;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员价「指定 SKU 查价」契约测试。
 *
 * <p>接口 {@code GET /app/member/prices} 供商详 / 购物车 / 列表二次聚合使用，本类守住：
 * <ol>
 *   <li><b>可选登录</b>：未登录返回活动配置价、等级与资格字段为 null，且不去查用户等级
 *       （只查活动门槛等级用于引导文案）；</li>
 *   <li><b>资格 fail-closed</b>：无等级 / 用户等级 RPC 异常 → eligible=false，不抛异常
 *       （展示失败不该阻断页面，真正拦截在下单链路）；</li>
 *   <li><b>门槛 fail-closed</b>：活动门槛等级查不到/异常时跳过该活动（无法给出正确等级名与资格）；</li>
 *   <li><b>降级不阻断</b>：商品远程查询异常 / 规则损坏 / 无交集 SKU 返回 null，不能 500；</li>
 *   <li><b>请求上限</b>：单次最多查询 200 个 SKU（超出静默忽略）。</li>
 * </ol>
 */
class MemberPriceActivityServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private PromotionActivityMapper activityMapper;

	private RemoteGoodsSkuService goodsSkuService;

	private RemoteMemberLevelService memberLevelService;

	private RemoteMallUserService mallUserService;

	private MemberPriceActivityServiceImpl service;

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
		memberLevelService = mock(RemoteMemberLevelService.class);
		// 构造器只接收两个 final 依赖；会员等级是 @DubboReference 非 final 字段（boot 启动约束），反射注入
		mallUserService = mock(RemoteMallUserService.class);
		service = new MemberPriceActivityServiceImpl(activityMapper, goodsSkuService);
		ReflectionTestUtils.setField(service, "remoteMemberLevelService", memberLevelService);
		// 会员折扣率权益也是 @DubboReference 非 final 字段（boot 构造器注入会启动失败），同样反射注入
		ReflectionTestUtils.setField(service, "remoteMallUserService", mallUserService);
		ArynTenantContextHolder.setTenantId(TENANT);
		// 门槛等级默认存在（引导文案用），各用例按需覆盖
		when(memberLevelService.getLevelById(any(), eq("lv-1"))).thenReturn(level("lv-1", "白银", 2));
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
		assertNull(service.getSkuPrices(List.of("", "   "), USER));

		verify(activityMapper, never()).selectList(any());
		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	// ==== 用例 2：请求 SKU 全未命中活动配置 ====

	@Test
	@DisplayName("请求 SKU 未命中活动配置时返回 null")
	void skusNotCoveredByActivityReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-9", "8.8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		assertNull(service.getSkuPrices(List.of("sku-1"), USER));
	}

	// ==== 用例 3：未登录（可选登录）====

	@Test
	@DisplayName("未登录时下发活动配置价，eligible 与用户等级为 null，且不查用户等级")
	void anonymousReturnsConfiguredPriceWithoutEligibility() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9", "sku-2", "8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-1", "29.9"), sku("sku-2", "spu-2", "20"), sku("sku-3", "spu-3", "15")));

		AppMemberPriceVO result = service.getSkuPrices(List.of("sku-1", "sku-3"), null);

		assertNotNull(result);
		assertNull(result.getEligible());
		assertNull(result.getUserLevelId());
		assertNull(result.getUserLevelName());
		// 只下发「请求 ∩ 活动配置」的交集
		assertEquals(1, result.getItems().size());
		AppMemberPriceVO.MemberSkuPriceVO item = result.getItems().get(0);
		assertEquals("sku-1", item.getSkuId());
		assertEquals("spu-1", item.getSpuId());
		assertEquals(0, item.getMemberPrice().compareTo(new BigDecimal("9.9")));
		// 划线价取商品库当前售价，而不是活动价
		assertEquals(0, item.getOriginalPrice().compareTo(new BigDecimal("29.9")));
		// 引导文案所需门槛等级信息
		assertEquals("lv-1", item.getMinLevelId());
		assertEquals("白银", item.getMinLevelName());

		verify(memberLevelService, never()).getUserLevel(any(), any());
	}

	// ==== 用例 4：登录且等级达标 ====

	@Test
	@DisplayName("登录且用户等级 sortOrder 达标时 eligible=true，并下发用户等级")
	void loggedInEligibleReturnsTrue() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));

		AppMemberPriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertTrue(result.getEligible());
		assertEquals("lv-user", result.getUserLevelId());
		assertEquals("金卡", result.getUserLevelName());
	}

	// ==== 用例 5：登录但等级不达标 / 无等级 ====

	@Test
	@DisplayName("登录但等级 sortOrder 低于门槛时 eligible=false，价格仍下发做营销引导")
	void loggedInBelowThresholdReturnsFalse() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-new", "普通会员", 1));

		AppMemberPriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertFalse(result.getEligible());
		assertEquals("lv-new", result.getUserLevelId());
		assertEquals(1, result.getItems().size());
	}

	@Test
	@DisplayName("登录但用户无等级（远程返回 null）时 eligible=false，用户等级字段为空")
	void loggedInWithoutLevelReturnsFalse() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(null);

		AppMemberPriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertFalse(result.getEligible());
		assertNull(result.getUserLevelId());
		assertNull(result.getUserLevelName());
	}

	// ==== 用例 6：等级查询异常 fail-closed ====

	@Test
	@DisplayName("用户等级查询异常时 eligible=false 且不抛出（fail-closed）")
	void userLevelErrorFailsClosedWithoutThrowing() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(any(), any())).thenThrow(new RuntimeException("dubbo timeout"));

		AppMemberPriceVO result = assertDoesNotThrow(() -> service.getSkuPrices(List.of("sku-1"), USER));

		assertNotNull(result);
		assertFalse(result.getEligible());
		assertEquals(1, result.getItems().size());
	}

	@Test
	@DisplayName("门槛等级查询异常时跳过该活动（返回 null），不把未知门槛当成无门槛")
	void thresholdLevelErrorSkipsActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getLevelById(any(), any())).thenThrow(new RuntimeException("dubbo timeout"));

		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));
	}

	@Test
	@DisplayName("门槛等级不存在（远程返回 null）时跳过该活动")
	void thresholdLevelMissingSkipsActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getLevelById(any(), any())).thenReturn(null);

		assertNull(service.getSkuPrices(List.of("sku-1"), USER));
	}

	// ==== 用例 7：商品远程查询异常降级 ====

	@Test
	@DisplayName("商品远程查询抛异常时返回 null（降级剔除，不阻断）")
	void goodsQueryErrorReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenThrow(new RuntimeException("product service down"));

		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));
	}

	// ==== 用例 8：单次查询 SKU 上限 ====

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
			skuRules.append("{\"skuId\":\"").append(requested.get(i)).append("\",\"unitPrice\":9.9}");
			if (i < 200) {
				goods.add(sku(requested.get(i), "spu-" + i, "29.9"));
			}
		}
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "{\"minLevelId\":\"lv-1\",\"skus\":[" + skuRules + "]}")));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(goods);

		AppMemberPriceVO result = service.getSkuPrices(requested, USER);

		assertNotNull(result);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuService).getBySkuIds(captor.capture());
		assertEquals(200, captor.getValue().size());
		assertEquals(requested.subList(0, 200), captor.getValue());
		assertEquals(200, result.getItems().size());
	}

	// ==== 用例 9：多活动并存时按优先级回落 ====

	@Test
	@DisplayName("高优先级活动规则损坏时回落到低优先级活动；等级快照按请求缓存")
	void fallsThroughToLowerPriorityActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				// mapper 按 priority 降序返回：先到的高优先级活动规则损坏
				activity("act-broken", "{broken json"),
				activity("act-low", memberRules("lv-1", "sku-1", "8.8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		AppMemberPriceVO result = service.getSkuPrices(List.of("sku-1"), USER);

		assertNotNull(result);
		assertEquals(0, result.getItems().get(0).getMemberPrice().compareTo(new BigDecimal("8.8")));
	}

	@Test
	@DisplayName("两个活动共用同一门槛等级时只查一次等级快照")
	void thresholdLevelCachedWithinRequest() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				// 第一个活动只配了 sku-9（与请求无交集，仍会解析门槛等级）
				activity("act-1", memberRules("lv-1", "sku-9", "1")),
				activity("act-2", memberRules("lv-1", "sku-1", "8.8"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		assertNotNull(service.getSkuPrices(List.of("sku-1"), USER));

		verify(memberLevelService, times(1)).getLevelById(any(), eq("lv-1"));
	}

	// ==== 用例 10：规则损坏 / 无 skus ====

	@Test
	@DisplayName("rules 损坏、缺少 skus 或缺少 minLevelId 时返回 null 且不抛异常")
	void brokenOrInvalidRulesSkipped() {
		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-broken", "{broken json")));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));

		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-empty", "{\"minLevelId\":\"lv-1\"}")));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));

		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-no-level", "{\"skus\":[{\"skuId\":\"sku-1\",\"unitPrice\":9.9}]}")));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));

		// rules 为 null（历史数据可能没回填）同样只跳过，不阻断
		when(activityMapper.selectList(any())).thenReturn(List.of(activity("act-null", null)));
		assertDoesNotThrow(() -> assertNull(service.getSkuPrices(List.of("sku-1"), USER)));
	}

	// ==== 用例 11：按 SPU 查询（商品列表卡片场景，F3 列表会员价聚合） ====

	@Test
	@DisplayName("spuIds 为空/null/全空白时直接返回 null，不查库")
	void emptySpuIdsReturnsNullWithoutQuery() {
		assertNull(service.getSpuPrices(null, USER));
		assertNull(service.getSpuPrices(List.of(), USER));
		assertNull(service.getSpuPrices(List.of("", "   "), USER));

		verify(activityMapper, never()).selectList(any());
		verify(goodsSkuService, never()).getBySkuIds(any());
	}

	@Test
	@DisplayName("spuIds 命中：同一 SPU 多个 SKU 全部返回（后端不挑选），未登录 eligible=null")
	void spuPricesReturnAllSkuItemsOfMatchedSpu() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9", "sku-2", "19.9", "sku-9", "5"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				sku("sku-1", "spu-1", "29.9"), sku("sku-2", "spu-1", "39.9"), sku("sku-9", "spu-9", "10")));

		AppMemberPriceVO result = service.getSpuPrices(List.of("spu-1"), null);

		assertNotNull(result);
		assertNull(result.getEligible());
		assertNull(result.getUserLevelId());
		// 同一 SPU 下两个规格都命中会员价：逐 SKU 全量返回，由 C 端按卡片展示 SKU 匹配
		assertEquals(2, result.getItems().size());
		assertTrue(result.getItems().stream().allMatch(item -> "spu-1".equals(item.getSpuId())));
		AppMemberPriceVO.MemberSkuPriceVO first = result.getItems().get(0);
		assertEquals("sku-1", first.getSkuId());
		assertEquals(0, first.getMemberPrice().compareTo(new BigDecimal("9.9")));
		// 划线价取该 SKU 当前售价，而不是活动价/SPU 聚合价
		assertEquals(0, first.getOriginalPrice().compareTo(new BigDecimal("29.9")));
		assertEquals("lv-1", first.getMinLevelId());
		assertEquals("白银", first.getMinLevelName());
		// 未登录不查用户等级（门槛等级仍查，用于引导文案）
		verify(memberLevelService, never()).getUserLevel(any(), any());
		// 回填商品用的是「活动配置的 SKU」而非请求的 spuIds
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuService).getBySkuIds(captor.capture());
		assertEquals(List.of("sku-1", "sku-2", "sku-9"), captor.getValue());
	}

	@Test
	@DisplayName("spuIds 未命中活动配置的 SKU 时返回 null")
	void spuPricesReturnNullWhenNoRequestedSpuMatched() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		assertNull(service.getSpuPrices(List.of("spu-404"), USER));
	}

	@Test
	@DisplayName("spuIds 查询：未登录/不达标/达标三态资格判定")
	void spuPricesEligibilityThreeStates() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		// 未登录：eligible=null，不下发用户等级，也不查用户等级
		AppMemberPriceVO anonymous = service.getSpuPrices(List.of("spu-1"), null);
		assertNotNull(anonymous);
		assertNull(anonymous.getEligible());
		assertNull(anonymous.getUserLevelId());
		verify(memberLevelService, never()).getUserLevel(any(), any());

		// 登录但不达标：eligible=false，价格仍下发做营销引导
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-new", "普通会员", 1));
		AppMemberPriceVO below = service.getSpuPrices(List.of("spu-1"), USER);
		assertNotNull(below);
		assertFalse(below.getEligible());
		assertEquals("lv-new", below.getUserLevelId());
		assertEquals(1, below.getItems().size());

		// 登录且达标：eligible=true
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));
		AppMemberPriceVO eligible = service.getSpuPrices(List.of("spu-1"), USER);
		assertNotNull(eligible);
		assertTrue(eligible.getEligible());
		assertEquals("lv-user", eligible.getUserLevelId());
	}

	@Test
	@DisplayName("spuIds 请求超限截断：只处理前 50 个 SPU（第 51 个及之后静默忽略）")
	void capsRequestedSpuIdsAtMaxPriceQuerySpus() {
		List<String> requested = IntStream.range(0, 60).mapToObj(i -> "spu-" + i).toList();
		// 活动只配了第 59 个 SPU 下的 SKU：超出 50 上限后不在请求集合内，不返回
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-59", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-59", "spu-59", "29.9")));

		assertNull(service.getSpuPrices(requested, USER));
	}

	@Test
	@DisplayName("配置 SKU 超限保护：只查前 500 个配置 SKU 回填商品（不报错）")
	void capsConfiguredSkusAtMaxConfiguredSkus() {
		StringBuilder rules = new StringBuilder();
		for (int i = 0; i < 600; i++) {
			if (i > 0) {
				rules.append(',');
			}
			rules.append("{\"skuId\":\"sku-").append(i).append("\",\"unitPrice\":9.9}");
		}
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", "{\"minLevelId\":\"lv-1\",\"skus\":[" + rules + "]}")));
		// 商品库对任意 skuId 都回填同构 SKU：sku-i → spu-i
		when(goodsSkuService.getBySkuIds(any())).thenAnswer(invocation -> {
			List<String> ids = invocation.getArgument(0);
			List<GoodsSku> goods = new ArrayList<>();
			for (String id : ids) {
				goods.add(sku(id, "spu-" + id.substring("sku-".length()), "29.9"));
			}
			return goods;
		});

		// 请求 spu-0（前 500 个配置 SKU 内）与 spu-599（被截断）：只返回前者
		AppMemberPriceVO result = service.getSpuPrices(List.of("spu-0", "spu-599"), USER);

		assertNotNull(result);
		assertEquals(1, result.getItems().size());
		assertEquals("spu-0", result.getItems().get(0).getSpuId());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuService).getBySkuIds(captor.capture());
		assertEquals(500, captor.getValue().size());
	}

	@Test
	@DisplayName("spuIds 查询：商品 RPC 异常降级返回 null，不抛异常")
	void spuPriceGoodsQueryErrorReturnsNull() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenThrow(new RuntimeException("product service down"));

		assertDoesNotThrow(() -> assertNull(service.getSpuPrices(List.of("spu-1"), USER)));
	}

	@Test
	@DisplayName("spuIds 查询：门槛等级查询异常时跳过活动（fail-closed），不抛异常")
	void spuPriceThresholdLevelErrorSkipsActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getLevelById(any(), any())).thenThrow(new RuntimeException("dubbo timeout"));

		assertDoesNotThrow(() -> assertNull(service.getSpuPrices(List.of("spu-1"), USER)));
	}

	@Test
	@DisplayName("spuIds 查询：高优先级活动未覆盖请求 SPU 时回落到次高活动")
	void spuPriceFallsThroughToLowerPriorityActivity() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				// 高优先级活动只配了 sku-9（spu-9），与请求 spu-1 无交集
				activity("act-high", memberRules("lv-1", "sku-9", "1")),
				activity("act-low", memberRules("lv-1", "sku-1", "8.8"))));
		when(goodsSkuService.getBySkuIds(any()))
			.thenReturn(List.of(sku("sku-1", "spu-1", "29.9"), sku("sku-9", "spu-9", "10")));

		AppMemberPriceVO result = service.getSpuPrices(List.of("spu-1"), USER);

		assertNotNull(result);
		assertEquals(1, result.getItems().size());
		assertEquals("sku-1", result.getItems().get(0).getSkuId());
		assertEquals(0, result.getItems().get(0).getMemberPrice().compareTo(new BigDecimal("8.8")));
	}

	// ==== 用例 12：会员折扣率 userDiscountRate（展示取优数据，member_benefit benefitType=1） ====

	@Test
	@DisplayName("登录且有折扣率权益：userDiscountRate=权益值，skuIds / spuIds 两条入口都下发")
	void userDiscountRateCarriedOnBothEntries() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));
		when(mallUserService.getMemberBenefits(USER)).thenReturn(benefits(new BigDecimal("0.9")));

		AppMemberPriceVO skuResult = service.getSkuPrices(List.of("sku-1"), USER);
		assertNotNull(skuResult);
		assertEquals(0, skuResult.getUserDiscountRate().compareTo(new BigDecimal("0.9")));

		AppMemberPriceVO spuResult = service.getSpuPrices(List.of("spu-1"), USER);
		assertNotNull(spuResult);
		assertEquals(0, spuResult.getUserDiscountRate().compareTo(new BigDecimal("0.9")));
	}

	@Test
	@DisplayName("登录但无折扣率权益（benefits 为空 / discountRate 为 null / 1）：userDiscountRate=1")
	void userDiscountRateFallsBackToOneWhenAbsent() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));

		// RPC 返回 null（用户不存在等极端情况）
		when(mallUserService.getMemberBenefits(USER)).thenReturn(null);
		assertEquals(0, service.getSkuPrices(List.of("sku-1"), USER).getUserDiscountRate()
			.compareTo(BigDecimal.ONE));

		// 等级未配置折扣率权益：discountRate 为 null → 1
		when(mallUserService.getMemberBenefits(USER)).thenReturn(benefits(null));
		assertEquals(0, service.getSkuPrices(List.of("sku-1"), USER).getUserDiscountRate()
			.compareTo(BigDecimal.ONE));

		// 显式 1 = 无折扣权益
		when(mallUserService.getMemberBenefits(USER)).thenReturn(benefits(BigDecimal.ONE));
		assertEquals(0, service.getSkuPrices(List.of("sku-1"), USER).getUserDiscountRate()
			.compareTo(BigDecimal.ONE));
	}

	@Test
	@DisplayName("折扣率脏值（≤0 或 >1）一律回落 1，不把非法权益当折扣下发")
	void userDiscountRateFallsBackToOneWhenInvalid() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));

		for (String invalid : List.of("0", "-0.1", "1.2")) {
			when(mallUserService.getMemberBenefits(USER)).thenReturn(benefits(new BigDecimal(invalid)));
			assertEquals(0, service.getSkuPrices(List.of("sku-1"), USER).getUserDiscountRate()
				.compareTo(BigDecimal.ONE), "非法折扣率 " + invalid + " 应回落 1");
		}
	}

	@Test
	@DisplayName("会员权益 RPC 异常：userDiscountRate=1 且仍返回会员价，不报错（展示 fail-open 到不打折）")
	void userDiscountRateRpcErrorFallsBackToOne() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));
		when(mallUserService.getMemberBenefits(USER)).thenThrow(new RuntimeException("dubbo timeout"));

		AppMemberPriceVO result = assertDoesNotThrow(() -> service.getSkuPrices(List.of("sku-1"), USER));

		assertNotNull(result);
		assertEquals(1, result.getItems().size());
		assertEquals(0, result.getUserDiscountRate().compareTo(BigDecimal.ONE));
		assertTrue(result.getEligible());
	}

	@Test
	@DisplayName("未登录：userDiscountRate=null，且不调用 getMemberBenefits")
	void userDiscountRateNullForAnonymous() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", memberRules("lv-1", "sku-1", "9.9"))));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(sku("sku-1", "spu-1", "29.9")));

		AppMemberPriceVO skuResult = service.getSkuPrices(List.of("sku-1"), null);
		assertNotNull(skuResult);
		assertNull(skuResult.getUserDiscountRate());

		AppMemberPriceVO spuResult = service.getSpuPrices(List.of("spu-1"), null);
		assertNotNull(spuResult);
		assertNull(spuResult.getUserDiscountRate());

		verify(mallUserService, never()).getMemberBenefits(any());
	}

	@Test
	@DisplayName("会员折扣率每请求只查一次（多活动回落时不重复 RPC）")
	void userDiscountRateQueriedOncePerRequest() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-high", memberRules("lv-1", "sku-9", "1")),
				activity("act-low", memberRules("lv-1", "sku-1", "8.8"))));
		when(goodsSkuService.getBySkuIds(any()))
			.thenReturn(List.of(sku("sku-1", "spu-1", "29.9"), sku("sku-9", "spu-9", "10")));
		when(memberLevelService.getUserLevel(TENANT, USER)).thenReturn(level("lv-user", "金卡", 3));
		when(mallUserService.getMemberBenefits(USER)).thenReturn(benefits(new BigDecimal("0.95")));

		AppMemberPriceVO result = service.getSpuPrices(List.of("spu-1"), USER);

		assertNotNull(result);
		verify(mallUserService, times(1)).getMemberBenefits(USER);
	}

	// ==== 测试数据构造 ====

	/**
	 * 构造会员价活动。
	 *
	 * <p>activeActivities() 的过滤条件（类型 10、已发布、时间窗内、优先级降序）由 SQL 承担，
	 * mapper 在单测里是 mock，这里只需保证类型/状态与生产一致，列表顺序即「优先级降序」。
	 */
	private PromotionActivity activity(String id, String rules) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setTenantId(TENANT);
		activity.setActivityName("会员价-" + id);
		activity.setActivityType(PromotionActivity.TYPE_MEMBER_PRICE);
		activity.setScopeType("2");
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setStartTime(LocalDateTime.now().minusDays(1));
		activity.setEndTime(LocalDateTime.now().plusDays(3));
		activity.setRules(rules);
		return activity;
	}

	/**
	 * rules JSON（与发布校验写入的结构一致）：minLevelId + skus[{skuId,unitPrice}]。
	 *
	 * @param skuPricePairs skuId、unitPrice 交替传入，如 ("sku-1", "9.9")
	 */
	private String memberRules(String minLevelId, String... skuPricePairs) {
		StringBuilder skus = new StringBuilder();
		for (int i = 0; i < skuPricePairs.length; i += 2) {
			if (i > 0) {
				skus.append(',');
			}
			skus.append("{\"skuId\":\"")
				.append(skuPricePairs[i])
				.append("\",\"unitPrice\":")
				.append(skuPricePairs[i + 1])
				.append('}');
		}
		return "{\"minLevelId\":\"" + minLevelId + "\",\"skus\":[" + skus + "]}";
	}

	private GoodsSku sku(String id, String spuId, String salesPrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		return sku;
	}

	private MemberBenefitsVO benefits(BigDecimal discountRate) {
		MemberBenefitsVO vo = new MemberBenefitsVO();
		vo.setDiscountRate(discountRate);
		return vo;
	}

	private MemberLevelSnapshotVO level(String levelId, String levelName, int sortOrder) {
		MemberLevelSnapshotVO vo = new MemberLevelSnapshotVO();
		vo.setLevelId(levelId);
		vo.setLevelName(levelName);
		vo.setSortOrder(sortOrder);
		return vo;
	}

}
