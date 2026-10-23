package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.aryn.cloud.promotion.service.impl.PromotionEngineServiceImpl;
import com.aryn.cloud.promotion.service.impl.PromotionLockUsage;
import com.aryn.cloud.promotion.service.impl.PromotionReservationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 船供营销引擎与锁定契约测试。
 */
class PromotionEngineTest {

	private static final String TENANT = "tenant-1";

	private PromotionActivityMapper activityMapper;

	private PromotionLockMapper lockMapper;

	private com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService buyerProfileService;

	private com.aryn.cloud.product.api.remote.RemoteGoodsSkuService goodsSkuService;

	private PromotionEngineServiceImpl engine;

	private PromotionReservationServiceImpl reservation;

	private PromotionLockUsage lockUsage;

	private com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService governanceService;

	@BeforeEach
	void setUp() {
		activityMapper = mock(PromotionActivityMapper.class);
		lockMapper = mock(PromotionLockMapper.class);
		buyerProfileService = mock(com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService.class);
		lockUsage = new PromotionLockUsage(lockMapper);
		engine = new PromotionEngineServiceImpl(activityMapper, lockUsage);
		ReflectionTestUtils.setField(engine, "remoteBuyerProfileService", buyerProfileService);
		goodsSkuService = mock(com.aryn.cloud.product.api.remote.RemoteGoodsSkuService.class);
		ReflectionTestUtils.setField(engine, "remoteGoodsSkuService", goodsSkuService);
		governanceService = new com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService(activityMapper);
		reservation = new PromotionReservationServiceImpl(engine, activityMapper, lockMapper, lockUsage);
		// 默认额度充足；竞态用例单独覆盖为 0
		when(activityMapper.consumeBudget(any(), any(), any())).thenReturn(1);
	}

	private PromotionContextDTO context(String scene, String vesselId, String callId, String portCode) {
		PromotionContextDTO context = new PromotionContextDTO();
		context.setTenantId(TENANT);
		context.setUserId("user-1");
		context.setPurchaseScene(scene);
		context.setVesselId(vesselId);
		context.setVesselCallId(callId);
		context.setPortCode(portCode);
		context.setSkuItems(List.of(skuItem("sku-1", 60, "28.5"), skuItem("sku-2", 5, "12")));
		return context;
	}

	private PromotionContextDTO.SkuItem skuItem(String skuId, int qty, String price) {
		PromotionContextDTO.SkuItem item = new PromotionContextDTO.SkuItem();
		item.setSkuId(skuId);
		item.setQuantity(qty);
		item.setSalesPrice(new BigDecimal(price));
		return item;
	}

	private PromotionActivity activity(String id, String type, String scopeType, String scopeValue, String scene,
			String rules) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId(id);
		activity.setTenantId(TENANT);
		activity.setActivityType(type);
		activity.setActivityName("活动-" + id);
		activity.setScopeType(scopeType);
		activity.setScopeValue(scopeValue);
		activity.setPurchaseScene(scene);
		activity.setRules(rules);
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setStartTime(LocalDateTime.now().minusDays(1));
		activity.setEndTime(LocalDateTime.now().plusDays(1));
		return activity;
	}

	@Test
	@DisplayName("阶梯价按数量命中最高档并生成单价覆盖")
	void ladderPricePicksHighestQualifiedTier() {
		String rules = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":10,\"unitPrice\":26},{\"minQty\":50,\"unitPrice\":24}]}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", "2", rules)));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertEquals(1, result.getLadderOverrides().size());
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("24")));
		assertEquals(0, result.getTotalDiscount().compareTo(new BigDecimal("270")));
	}

	@Test
	@DisplayName("数量未达任何档位或档价不低于基价时不命中")
	void ladderPriceSkipsUnqualified() {
		String rules = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":100,\"unitPrice\":20},{\"minQty\":5,\"unitPrice\":30}]}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", null, rules)));

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertTrue(result.getLadderOverrides().isEmpty());
		assertEquals(0, result.getTotalDiscount().compareTo(BigDecimal.ZERO));
	}

	@Test
	@DisplayName("整船优惠按金额命中档位，同类取最优")
	void wholeDiscountPicksBestTierAndBestActivity() {
		String tierLow = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		String tierHigh = "{\"tiers\":[{\"minAmount\":500,\"discountAmount\":80}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-low", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", tierLow),
				activity("act-high", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", tierHigh)));

		// 基价 60*28.5 + 5*12 = 1770：命中 1000/100 与 500/80，取最优 100
		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertEquals(0, result.getWholeDiscount().compareTo(new BigDecimal("100")));
		assertEquals(1, result.getDetails().size());
		assertEquals("act-low", result.getDetails().get(0).getActivityId());
	}

	@Test
	@DisplayName("范围不匹配的活动不参与计算")
	void scopeMismatchSkipped() {
		String rules = "{\"tiers\":[{\"minAmount\":100,\"discountAmount\":50}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-vessel", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "5", "vessel-other", null, rules),
				activity("act-scene", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "4", "1", null, rules)));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertTrue(result.getDetails().isEmpty());
	}

	@Test
	@DisplayName("已享阶梯价的 SKU 不计入整船优惠门槛")
	void ladderPricedSkuIsExcludedFromWholeDiscountBase() {
		String ladder = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":50,\"unitPrice\":24}]}]}";
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-ladder", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", "2", ladder),
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		// 阶梯价 SKU 原价 1710 被排除，仅剩 sku-2 的 60 参与门槛，未达 1000
		assertEquals(1, result.getDetails().size());
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("24")));
		assertEquals(0, result.getWholeDiscount().compareTo(BigDecimal.ZERO));
	}

	@Test
	@DisplayName("外部活动价标记的 SKU 不计入整船优惠门槛")
	void externallyActivityPricedSkuIsExcludedFromWholeDiscountBase() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		// sku-1 已享秒杀/折扣等价，原价 1710 退出门槛；sku-2 的 60 不足以命中
		context.getSkuItems().get(0).setActivityPriced(true);
		PromotionCalculationVO result = engine.preview(context);

		assertTrue(result.getDetails().isEmpty());
		assertEquals(0, result.getWholeDiscount().compareTo(BigDecimal.ZERO));
	}

	@Test
	@DisplayName("整船优惠仍按未享活动价的商品金额命中档位")
	void wholeDiscountUsesOnlyNonActivityBase() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		PromotionContextDTO.SkuItem activityPriced = skuItem("sku-1", 60, "28.5");
		activityPriced.setActivityPriced(true);
		// 排除 sku-1 后，sku-2(60) + sku-3(1000) 仍达到 1000 档
		context.setSkuItems(List.of(activityPriced, skuItem("sku-2", 5, "12"), skuItem("sku-3", 10, "100")));

		PromotionCalculationVO result = engine.preview(context);
		assertEquals(0, result.getWholeDiscount().compareTo(new BigDecimal("100")));
	}

	@Test
	@DisplayName("reserve 写入锁定记录并返回计算结果")
	void reserveCreatesLocks() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		context.setOrderNo("ON001");
		PromotionCalculationVO result = reservation.reserve(context);

		assertEquals(0, result.getWholeDiscount().compareTo(new BigDecimal("100")));
		ArgumentCaptor<PromotionLock> captor = ArgumentCaptor.forClass(PromotionLock.class);
		verify(lockMapper).insert(captor.capture());
		assertEquals("order-1", captor.getValue().getOrderId());
		assertEquals(PromotionLock.STATUS_LOCKED, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("重复 reserve 幂等返回原锁定结果（赠品按锁快照恢复）")
	void reserveIsIdempotent() {
		PromotionLock existing = new PromotionLock();
		existing.setOrderId("order-1");
		existing.setActivityId("act-1");
		existing.setActivityType("7");
		existing.setDiscountAmount(new BigDecimal("100"));
		existing.setStatus(PromotionLock.STATUS_LOCKED);
		PromotionLock gift = new PromotionLock();
		gift.setOrderId("order-1");
		gift.setActivityId("act-gift");
		gift.setActivityType(PromotionActivity.TYPE_GIFT);
		gift.setDiscountAmount(BigDecimal.ZERO);
		gift.setRuleSnapshot("{\"minAmount\":100,\"gifts\":[{\"skuId\":\"g1\",\"quantity\":2}]}");
		gift.setStatus(PromotionLock.STATUS_LOCKED);
		when(lockMapper.selectOne(any())).thenReturn(existing);
		when(lockMapper.selectList(any())).thenReturn(List.of(existing, gift));

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		PromotionCalculationVO result = reservation.reserve(context);

		assertEquals(0, result.getWholeDiscount().compareTo(new BigDecimal("100")));
		assertEquals(2, result.getDetails().size());
		assertEquals(1, result.getGifts().size());
		assertEquals("g1", result.getGifts().get(0).getSkuId());
		assertEquals(2, result.getGifts().get(0).getQuantity());
		verify(lockMapper, org.mockito.Mockito.never()).insert(any(PromotionLock.class));
	}

	@Test
	@DisplayName("每人限享用完：活动不参与试算，订单可继续按其他优惠成交")
	void perUserLimitExhaustedActivityIsSkipped() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		PromotionActivity limited = activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole);
		limited.setPerUserLimit(1);
		when(activityMapper.selectList(any())).thenReturn(List.of(limited));
		when(activityMapper.selectById("act-1")).thenReturn(limited);
		when(lockMapper.selectOne(any())).thenReturn(null);
		when(lockMapper.selectCount(any())).thenReturn(1L);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		PromotionCalculationVO result = reservation.reserve(context);

		assertTrue(result.getDetails().isEmpty());
		assertEquals(0, result.getTotalDiscount().compareTo(BigDecimal.ZERO));
		verify(lockMapper, org.mockito.Mockito.never()).insert(any(PromotionLock.class));
	}

	@Test
	@DisplayName("预算不足：活动不参与试算，订单按原价继续，不再整单拦截")
	void budgetExhaustedActivityIsSkipped() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		PromotionActivity budgeted = activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole);
		budgeted.setTotalBudget(new BigDecimal("50"));
		budgeted.setConsumedBudget(BigDecimal.ZERO);
		when(activityMapper.selectList(any())).thenReturn(List.of(budgeted));
		when(activityMapper.selectById("act-1")).thenReturn(budgeted);
		when(lockMapper.selectOne(any())).thenReturn(null);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		PromotionCalculationVO result = reservation.reserve(context);

		assertTrue(result.getDetails().isEmpty());
		assertEquals(0, result.getTotalDiscount().compareTo(BigDecimal.ZERO));
		verify(lockMapper, org.mockito.Mockito.never()).insert(any(PromotionLock.class));
	}

	@Test
	@DisplayName("预算已被并发订单用尽：reserve 拒绝并提示刷新价格")
	void reserveRejectsWhenBudgetConsumedConcurrently() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		PromotionActivity budgeted = activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole);
		budgeted.setTotalBudget(new BigDecimal("500"));
		budgeted.setConsumedBudget(BigDecimal.ZERO);
		when(activityMapper.selectList(any())).thenReturn(List.of(budgeted));
		when(activityMapper.selectById("act-1")).thenReturn(budgeted);
		when(lockMapper.selectOne(any())).thenReturn(null);
		when(activityMapper.consumeBudget(any(), any(), any())).thenReturn(0);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		assertThrows(ArynBusinessException.class, () -> reservation.reserve(context));
	}

	@Test
	@DisplayName("预算充足的锁定会原子占用让利额")
	void reserveConsumesBudget() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		PromotionActivity budgeted = activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole);
		budgeted.setTotalBudget(new BigDecimal("500"));
		budgeted.setConsumedBudget(BigDecimal.ZERO);
		when(activityMapper.selectList(any())).thenReturn(List.of(budgeted));
		when(activityMapper.selectById("act-1")).thenReturn(budgeted);
		when(lockMapper.selectOne(any())).thenReturn(null);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		reservation.reserve(context);

		verify(activityMapper).consumeBudget(TENANT, "act-1", new BigDecimal("100"));
	}

	@Test
	@DisplayName("并发锁定命中唯一约束时幂等跳过")
	void concurrentReserveSkipsDuplicate() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));
		when(lockMapper.selectOne(any())).thenReturn(null);
		org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_promotion_lock"))
			.when(lockMapper).insert(any(PromotionLock.class));

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		PromotionCalculationVO result = reservation.reserve(context);
		assertEquals(1, result.getDetails().size());
	}

	@Test
	@DisplayName("已释放的订单不能重复锁定")
	void releasedOrderCannotRelock() {
		PromotionLock released = new PromotionLock();
		released.setStatus(PromotionLock.STATUS_RELEASED);
		when(lockMapper.selectOne(any())).thenReturn(released);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		assertThrows(com.aryn.cloud.common.security.handler.ArynBusinessException.class,
				() -> reservation.reserve(context));
	}

	@Test
	@DisplayName("confirm/release 推进锁定状态且幂等")
	void confirmAndReleaseIdempotent() {
		PromotionLock locked = new PromotionLock();
		locked.setOrderId("order-1");
		locked.setActivityId("act-1");
		locked.setDiscountAmount(new BigDecimal("100"));
		locked.setStatus(PromotionLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(locked));

		reservation.confirm(TENANT, "order-1");
		assertEquals(PromotionLock.STATUS_CONFIRMED, locked.getStatus());

		reservation.release(TENANT, "order-1", "CANCEL");
		assertEquals(PromotionLock.STATUS_RELEASED, locked.getStatus());
		assertEquals("CANCEL", locked.getReleaseReason());
		verify(activityMapper).releaseBudget(TENANT, "act-1", new BigDecimal("100"));

		// 再次 release 已释放记录：状态与预算归还都不重复（幂等）
		reservation.release(TENANT, "order-1", "TIMEOUT");
		assertEquals("CANCEL", locked.getReleaseReason());
		verify(activityMapper, org.mockito.Mockito.times(1)).releaseBudget(TENANT, "act-1",
				new BigDecimal("100"));
	}

	@Test
	@DisplayName("规则 JSON 损坏的活动被跳过不阻断结算")
	void brokenRulesSkipped() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-bad", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", "{broken json")));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertTrue(result.getDetails().isEmpty());
	}



	@Test
	@DisplayName("阶梯价档位重复、单价不递减、范围不合法时发布校验拒绝")
	void validateLaddersRejectsBadTiers() {
		PromotionActivity activity = activity("act-1", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", null,
				"{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":10,\"unitPrice\":26},{\"minQty\":10,\"unitPrice\":25}]}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		activity.setRules("{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":10,\"unitPrice\":24},{\"minQty\":50,\"unitPrice\":26}]}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		activity.setRules("{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":10,\"unitPrice\":26},{\"minQty\":50,\"unitPrice\":24}]}]}");
		governanceService.validateRules(activity);

		// 全场范围不再允许：单档价会串到所有商品
		activity.setScopeType("1");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		// skuLadders 与指定商品范围必须一一对应
		activity.setScopeType("2");
		activity.setScopeValue("sku-2");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));
	}

	@Test
	@DisplayName("整船优惠档位重复或优惠递减时发布校验拒绝")
	void validateTiersRejectsBadTiers() {
		PromotionActivity activity = activity("act-1", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, null,
				"{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100},{\"minAmount\":1000,\"discountAmount\":200}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		activity.setRules("{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":300},{\"minAmount\":2000,\"discountAmount\":200}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));
	}

	@Test
	@DisplayName("发布冲突：同类型时间重叠命中，不同类型或无重叠不命中")
	void listPublishConflictsScoping() {
		PromotionActivity mine = activity("act-new", PromotionActivity.TYPE_LADDER_PRICE, "1", null, null,
				"{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":10,\"unitPrice\":26}]}]}");
		mine.setStartTime(LocalDateTime.now().minusDays(1));
		mine.setEndTime(LocalDateTime.now().plusDays(1));

		PromotionActivity overlapping = activity("act-old", PromotionActivity.TYPE_LADDER_PRICE, "1", null, null, "{}");
		when(activityMapper.selectList(any())).thenReturn(List.of(overlapping));

		assertEquals(1, governanceService.listPublishConflicts(TENANT, mine).size());
	}


	@Test
	@DisplayName("买赠：基价达门槛时返回赠品列表，未达门槛不返回")
	void giftActivityThreshold() {
		String rules = "{\"minAmount\":1000,\"gifts\":[{\"skuId\":\"gift-sku\",\"quantity\":2}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-gift", PromotionActivity.TYPE_GIFT, "1", null, "2", rules)));

		// 基价 60*28.5 + 5*12 = 1770 ≥ 1000 → 命中
		PromotionCalculationVO hit = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertEquals(1, hit.getGifts().size());
		assertEquals("gift-sku", hit.getGifts().get(0).getSkuId());
		assertEquals(2, hit.getGifts().get(0).getQuantity());
		assertEquals(0, hit.getWholeDiscount().compareTo(BigDecimal.ZERO));

		// 仅保留低价小单 → 未达门槛
		PromotionContextDTO small = context("2", "vessel-1", "call-1", "CNSHA");
		small.setSkuItems(List.of(skuItem("sku-1", 1, "10")));
		PromotionCalculationVO miss = engine.preview(small);
		assertEquals(0, miss.getGifts().size());
	}

	@Test
	@DisplayName("买赠同类取最优：赠品总数量最大者命中")
	void giftActivityPicksBest() {
		String one = "{\"minAmount\":100,\"gifts\":[{\"skuId\":\"g1\",\"quantity\":1}]}";
		String two = "{\"minAmount\":100,\"gifts\":[{\"skuId\":\"g2\",\"quantity\":3}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_GIFT, "1", null, null, one),
				activity("act-2", PromotionActivity.TYPE_GIFT, "1", null, null, two)));

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertEquals(1, result.getGifts().size());
		assertEquals("g2", result.getGifts().get(0).getSkuId());
	}

	@Test
	@DisplayName("买赠同类取最优：赠品价值最大者命中（售价×数量）")
	void giftActivityPicksBestByValue() {
		String many = "{\"minAmount\":100,\"gifts\":[{\"skuId\":\"cheap\",\"quantity\":10}]}";
		String few = "{\"minAmount\":100,\"gifts\":[{\"skuId\":\"pricy\",\"quantity\":1}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-many", PromotionActivity.TYPE_GIFT, "1", null, null, many),
				activity("act-few", PromotionActivity.TYPE_GIFT, "1", null, null, few)));
		com.aryn.cloud.product.api.entity.GoodsSku cheap = new com.aryn.cloud.product.api.entity.GoodsSku();
		cheap.setId("cheap");
		cheap.setSalesPrice(new BigDecimal("0.5"));
		com.aryn.cloud.product.api.entity.GoodsSku pricy = new com.aryn.cloud.product.api.entity.GoodsSku();
		pricy.setId("pricy");
		pricy.setSalesPrice(new BigDecimal("100"));
		when(goodsSkuService.getBySkuIds(any())).thenReturn(List.of(cheap, pricy));

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertEquals(1, result.getGifts().size());
		assertEquals("pricy", result.getGifts().get(0).getSkuId());
	}

	@Test
	@DisplayName("买赠规则档位不合法时发布校验拒绝")
	void validateGiftsRejectsBad() {
		PromotionActivity activity = activity("act-1", PromotionActivity.TYPE_GIFT, "1", null, null,
				"{\"minAmount\":100,\"gifts\":[{\"skuId\":\"\",\"quantity\":0}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		activity.setRules("{\"minAmount\":100,\"gifts\":[{\"skuId\":\"g1\",\"quantity\":1}]}");
		governanceService.validateRules(activity);
	}


	@Test
	@DisplayName("常购专属价：用户常购且SKU在活动范围内命中单价覆盖")
	void frequentExclusivePriceMatches() {
		String rules = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":1,\"unitPrice\":25}]}]}";
		PromotionActivity activity = activity("act-freq", PromotionActivity.TYPE_LADDER_PRICE, "8", "sku-1", "2",
				rules);
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(buyerProfileService.frequentSkuIds(TENANT, "user-1")).thenReturn(List.of("sku-1"));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertEquals(1, result.getLadderOverrides().size());
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("25")));
	}

	@Test
	@DisplayName("常购画像查询失败时活动按不命中处理（fail-closed）")
	void frequentProfileFailureSkipsActivity() {
		String rules = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":1,\"unitPrice\":25}]}]}";
		PromotionActivity activity = activity("act-freq", PromotionActivity.TYPE_LADDER_PRICE, "8", "sku-1", "2",
				rules);
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(buyerProfileService.frequentSkuIds(any(), any())).thenThrow(new RuntimeException("rpc down"));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertTrue(result.getLadderOverrides().isEmpty());
	}

	@Test
	@DisplayName("用户非常购该SKU时常购专属价不命中")
	void nonFrequentUserSkipsExclusivePrice() {
		String rules = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":1,\"unitPrice\":25}]}]}";
		PromotionActivity activity = activity("act-freq", PromotionActivity.TYPE_LADDER_PRICE, "8", "sku-1", "2",
				rules);
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
		when(buyerProfileService.frequentSkuIds(TENANT, "user-1")).thenReturn(List.of("sku-other"));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertTrue(result.getLadderOverrides().isEmpty());
	}

	// ==== 新人专享价 ====

	private String newbieRules(String skuPrices) {
		return "{\"registerWithinDays\":30,\"limitPerUser\":1,\"skus\":[" + skuPrices + "]}";
	}

	private void newbieEligible(boolean eligible) {
		com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO vo = new com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO();
		vo.setEligible(eligible);
		vo.setReason(eligible ? null : "ALREADY_PAID");
		when(buyerProfileService.checkNewbieEligibility(org.mockito.ArgumentMatchers.eq(TENANT),
				org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any()))
			.thenReturn(vo);
	}

	@Test
	@DisplayName("新人专享价：资格通过时按 SKU 覆盖单价并记活动明细")
	void newbiePriceAppliesWhenEligible() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(true);

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertEquals(1, result.getLadderOverrides().size());
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("19.9")));
		assertEquals(PromotionActivity.TYPE_NEWBIE_PRICE, result.getLadderOverrides().get(0).getActivityType());
		assertEquals(1, result.getDetails().size());
		// 60 件 * (28.5-19.9) = 516
		assertEquals(0, result.getTotalDiscount().compareTo(new BigDecimal("516.0")));
	}

	@Test
	@DisplayName("新人价 SKU 不计入整船优惠门槛")
	void newbiePricedSkuIsExcludedFromWholeDiscountBase() {
		String newbie = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, newbie),
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, null, whole)));
		newbieEligible(true);

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		// 新人价 SKU 原价 1710 被排除，仅 sku-2 的 60 参与门槛，未达 1000
		assertEquals(1, result.getDetails().size());
		assertEquals(0, result.getWholeDiscount().compareTo(BigDecimal.ZERO));
	}

	@Test
	@DisplayName("新人专享价：已下过单（资格不合格）不产生覆盖")
	void newbiePriceSkippedWhenNotEligible() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(false);

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertTrue(result.getLadderOverrides().isEmpty());
		assertTrue(result.getDetails().isEmpty());
	}

	@Test
	@DisplayName("新人专享价：资格查询异常按非新人处理（fail-closed）")
	void newbiePriceFailClosedOnError() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		when(buyerProfileService.checkNewbieEligibility(any(), any(), any()))
			.thenThrow(new RuntimeException("rpc down"));

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertTrue(result.getLadderOverrides().isEmpty());
	}

	@Test
	@DisplayName("新人专享价：同 SKU 与阶梯价并存时取更低价并保留胜出活动ID")
	void newbiePriceTakesLowerPriceAgainstLadder() {
		String ladder = "{\"skuLadders\":[{\"skuId\":\"sku-1\",\"ladders\":[{\"minQty\":1,\"unitPrice\":25}]}]}";
		String newbie = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-ladder", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", null, ladder),
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, newbie)));
		newbieEligible(true);

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		// 新人价 19.9 < 阶梯价 25：同 SKU 收敛为一条覆盖，取新人价
		assertEquals(1, result.getLadderOverrides().size());
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("19.9")));
		assertEquals("act-newbie", result.getLadderOverrides().get(0).getActivityId());
	}

	@Test
	@DisplayName("新人专享价：命中价不低于基价时不产生覆盖")
	void newbiePriceSkippedWhenNotCheaper() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":30}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(true);

		PromotionCalculationVO result = engine.preview(context("1", null, null, null));
		assertTrue(result.getLadderOverrides().isEmpty());
		// 资格通过即命中：无差价也要产出明细，否则 reserve 落锁会静默丢失
		assertEquals(1, result.getDetails().size());
	}

	/**
	 * 下单链会对同一订单调用两次 preview（改基价一次、reserve 落锁一次），
	 * 第二次传入的基价已是新人价。此时必须仍然产出明细，否则 promotion_lock
	 * 不写入 → 在途唯一与优惠快照全部失效（真实缺陷，仅端到端可现）。
	 */
	@Test
	@DisplayName("新人专享价重放：基价已等于活动价时仍产出明细（供 reserve 落锁）")
	void newbiePriceReplayStillEmitsDetail() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(true);

		// 第一次：基价 28.5 → 覆盖为 19.9
		PromotionCalculationVO first = engine.preview(context("1", null, null, null));
		assertEquals(1, first.getLadderOverrides().size());

		// 第二次：基价已改成首次结果（模拟 order 侧 applyPromotionLadder 改写 salesPrice）
		PromotionContextDTO replay = context("1", null, null, null);
		replay.setSkuItems(List.of(skuItem("sku-1", 60, "19.9")));
		PromotionCalculationVO second = engine.preview(replay);
		assertTrue(second.getLadderOverrides().isEmpty(), "已是最低价不再重复覆盖");
		assertEquals(1, second.getDetails().size(), "重放仍须命中，锁才能落库");
		assertEquals(PromotionActivity.TYPE_NEWBIE_PRICE, second.getDetails().get(0).getActivityType());
	}

	@Test
	@DisplayName("新人专享价：资格查询结果按注册时效在本次 preview 内缓存")
	void newbieEligibilityCachedPerPreview() {
		String rules1 = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		String rules2 = newbieRules("{\"skuId\":\"sku-2\",\"price\":8}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie-1", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules1),
				activity("act-newbie-2", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-2", null, rules2)));
		newbieEligible(true);

		engine.preview(context("1", null, null, null));
		// 两个活动同为 registerWithinDays=30：仅一次远程判定
		verify(buyerProfileService, org.mockito.Mockito.times(1))
			.checkNewbieEligibility(any(), any(), any());
	}

	@Test
	@DisplayName("新人专享价：未登录（无 userId）不参与命中")
	void newbiePriceSkippedWhenAnonymous() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		PromotionContextDTO anonymous = context("1", null, null, null);
		anonymous.setUserId(null);

		PromotionCalculationVO result = engine.preview(anonymous);
		assertTrue(result.getLadderOverrides().isEmpty());
	}

	@Test
	@DisplayName("新人专享价发布校验：范围/限购/单价/一一对应")
	void validateNewbiePriceRules() {
		PromotionActivity activity = activity("act-1", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1,sku-2", null,
				newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9},{\"skuId\":\"sku-2\",\"price\":8}"));
		governanceService.validateRules(activity);

		// 非指定商品范围
		activity.setScopeType("1");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));
		activity.setScopeType("2");

		// 限购必须为 1
		activity.setRules("{\"registerWithinDays\":30,\"limitPerUser\":2,\"skus\":[{\"skuId\":\"sku-1\",\"price\":19.9}]}");
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		// skus 与 scopeValue 不一致
		activity.setRules(newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}"));
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));

		// 价格非正数
		activity.setScopeValue("sku-1");
		activity.setRules(newbieRules("{\"skuId\":\"sku-1\",\"price\":0}"));
		assertThrows(ArynBusinessException.class, () -> governanceService.validateRules(activity));
	}

	@Test
	@DisplayName("新人价在途唯一：同用户已有待完成新人价锁时拒绝重复锁定")
	void newbieInFlightUniqueRejectsSecondOrder() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(true);
		when(lockMapper.selectOne(any())).thenReturn(null);
		// 该用户已有一张 LOCKED 的新人价单
		when(lockMapper.selectCount(any())).thenReturn(1L);

		PromotionContextDTO context = context("1", null, null, null);
		context.setOrderId("order-2");
		assertThrows(ArynBusinessException.class, () -> reservation.reserve(context));
	}

	@Test
	@DisplayName("新人价在途唯一：本单未命中新人价时不检查、普通订单不受影响")
	void newbieInFlightUniqueSkipsWhenNotNewbieOrder() {
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));
		when(lockMapper.selectOne(any())).thenReturn(null);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-3");
		PromotionCalculationVO result = reservation.reserve(context);
		assertEquals(1, result.getDetails().size());
	}

	@Test
	@DisplayName("新人价在途唯一：已释放/已确认的锁不拦截")
	void newbieInFlightUniqueIgnoresNonLockedStatus() {
		String rules = newbieRules("{\"skuId\":\"sku-1\",\"price\":19.9}");
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-newbie", PromotionActivity.TYPE_NEWBIE_PRICE, "2", "sku-1", null, rules)));
		newbieEligible(true);
		when(lockMapper.selectOne(any())).thenReturn(null);
		when(lockMapper.selectCount(any())).thenReturn(0L);

		PromotionContextDTO context = context("1", null, null, null);
		context.setOrderId("order-4");
		PromotionCalculationVO result = reservation.reserve(context);
		assertEquals(1, result.getDetails().size());
		ArgumentCaptor<PromotionLock> captor = ArgumentCaptor.forClass(PromotionLock.class);
		verify(lockMapper).insert(captor.capture());
		assertEquals("user-1", captor.getValue().getUserId());
	}

}
