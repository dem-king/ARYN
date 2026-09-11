package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.aryn.cloud.promotion.service.impl.PromotionEngineServiceImpl;
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

	private PromotionEngineServiceImpl engine;

	private PromotionReservationServiceImpl reservation;

	@BeforeEach
	void setUp() {
		activityMapper = mock(PromotionActivityMapper.class);
		lockMapper = mock(PromotionLockMapper.class);
		engine = new PromotionEngineServiceImpl(activityMapper);
		reservation = new PromotionReservationServiceImpl(engine, lockMapper);
		ReflectionTestUtils.setField(reservation, "promotionEngineService", engine);
		ReflectionTestUtils.setField(reservation, "promotionLockMapper", lockMapper);
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
		String rules = "{\"ladders\":[{\"minQty\":10,\"unitPrice\":26},{\"minQty\":50,\"unitPrice\":24}]}";
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
		String rules = "{\"ladders\":[{\"minQty\":100,\"unitPrice\":20},{\"minQty\":5,\"unitPrice\":30}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-1", PromotionActivity.TYPE_LADDER_PRICE, "1", null, null, rules)));

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
	@DisplayName("跨类型按固定顺序叠加：阶梯价 + 整船优惠")
	void crossTypeStacksInOrder() {
		String ladder = "{\"ladders\":[{\"minQty\":50,\"unitPrice\":24}]}";
		String whole = "{\"tiers\":[{\"minAmount\":1000,\"discountAmount\":100}]}";
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-ladder", PromotionActivity.TYPE_LADDER_PRICE, "2", "sku-1", "2", ladder),
				activity("act-whole", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", whole)));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertEquals(2, result.getDetails().size());
		// 阶梯价节省 (28.5-24)*60=270；整单优惠 100（基价口径）
		assertEquals(0, result.getLadderOverrides().get(0).getUnitPrice().compareTo(new BigDecimal("24")));
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
	@DisplayName("重复 reserve 幂等返回原锁定结果")
	void reserveIsIdempotent() {
		PromotionLock existing = new PromotionLock();
		existing.setOrderId("order-1");
		existing.setActivityId("act-1");
		existing.setActivityType("7");
		existing.setDiscountAmount(new BigDecimal("100"));
		existing.setStatus(PromotionLock.STATUS_LOCKED);
		when(lockMapper.selectOne(any())).thenReturn(existing);

		PromotionContextDTO context = context("2", "vessel-1", "call-1", "CNSHA");
		context.setOrderId("order-1");
		PromotionCalculationVO result = reservation.reserve(context);

		assertEquals(0, result.getWholeDiscount().compareTo(new BigDecimal("100")));
		verify(lockMapper, org.mockito.Mockito.never()).insert(any(PromotionLock.class));
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
		locked.setStatus(PromotionLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(locked));

		reservation.confirm(TENANT, "order-1");
		assertEquals(PromotionLock.STATUS_CONFIRMED, locked.getStatus());

		reservation.release(TENANT, "order-1", "CANCEL");
		assertEquals(PromotionLock.STATUS_RELEASED, locked.getStatus());
		assertEquals("CANCEL", locked.getReleaseReason());

		// 再次 release 已释放记录：状态不变（幂等）
		reservation.release(TENANT, "order-1", "TIMEOUT");
		assertEquals("CANCEL", locked.getReleaseReason());
	}

	@Test
	@DisplayName("规则 JSON 损坏的活动被跳过不阻断结算")
	void brokenRulesSkipped() {
		when(activityMapper.selectList(any())).thenReturn(List.of(
				activity("act-bad", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT, "1", null, "2", "{broken json")));

		PromotionCalculationVO result = engine.preview(context("2", "vessel-1", "call-1", "CNSHA"));
		assertTrue(result.getDetails().isEmpty());
	}

}
