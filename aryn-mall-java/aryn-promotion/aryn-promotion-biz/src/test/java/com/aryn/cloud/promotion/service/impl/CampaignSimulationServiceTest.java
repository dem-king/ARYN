package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.promotion.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CampaignSimulationServiceTest {
	private CampaignSimulationService service;
	private CampaignSimulationMapper records;
	private B2bActivityCommandService commands;
	private RemoteOrderScenarioService order;
	private PromotionActivityMapper activities;
	private PromotionEngineServiceImpl engine;
	private PromotionActivity draft;
	private OrderScenarioFactsVO facts;
	private final AtomicReference<CampaignSimulationRecord> saved = new AtomicReference<>();

	@BeforeEach void setup() {
		ArynTenantContextHolder.setTenantId("tenant");
		records = mock(CampaignSimulationMapper.class); commands = mock(B2bActivityCommandService.class);
		order = mock(RemoteOrderScenarioService.class); activities = mock(PromotionActivityMapper.class);
		var governance = new ActivityPublishGovernanceService(activities);
		engine = new PromotionEngineServiceImpl(activities, mock(PricePolicyMapper.class), mock(PricePolicyItemMapper.class),
				mock(PromoCalendarMapper.class), new PromotionLockUsage(mock(PromotionLockMapper.class)));
		service = new CampaignSimulationService(records, commands, governance, engine, new ObjectMapper().findAndRegisterModules(), mock(CampaignDraftStore.class), mock(PricePolicyService.class));
		ReflectionTestUtils.setField(service, "remoteOrderScenarioService", order);
		draft = activity("draft", "20"); draft.setStatus(PromotionActivity.STATUS_DRAFT);
		when(commands.get("draft")).thenReturn(draft);
		when(activities.selectList(any())).thenReturn(List.of());
		facts = new OrderScenarioFactsVO(); facts.setContext(request().getSample()); facts.setSkus(List.of(sku()));
		when(order.capture(any(), any())).thenReturn(facts);
		when(order.compare(any(), any(), any())).thenAnswer(invocation -> comparison(invocation.getArgument(1), invocation.getArgument(2)));
		when(records.insert(any(CampaignSimulationRecord.class))).thenAnswer(invocation -> { saved.set(invocation.getArgument(0)); return 1; });
		when(records.selectOne(any())).thenAnswer(invocation -> saved.get());
	}
	@AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@Test void unpublishedDraftAffectsOnlySimulationAndOnlySimulationRecordIsWritten() {
		var result = service.simulate("operator", request());
		assertEquals(0, result.getBaseline().getTotalDiscount().signum());
		assertEquals(0, new BigDecimal("20").compareTo(result.getProposed().getWholeDiscount()));
		assertEquals(0, engine.preview(facts.getContext()).getTotalDiscount().signum());
		assertEquals(PromotionActivity.STATUS_DRAFT, draft.getStatus());
		assertTrue(result.isCurrent()); assertEquals(64, result.getFactDigest().length());
		verify(records).insert(any(CampaignSimulationRecord.class));
		verify(activities, never()).insert(any(PromotionActivity.class));
		verify(activities, never()).updateById(any(PromotionActivity.class));
		verify(commands, never()).publish(any(), any(), anyBoolean());
		assertTrue(service.get("operator", result.getId(), true).isCurrent());
	}

	@Test void replaceRemovesOldRuleRatherThanApplyingBoth() {
		var existing = activity("old", "50"); when(commands.get("old")).thenReturn(existing);
		when(activities.selectList(any())).thenReturn(List.of(existing));
		var request = request(); request.setReplacesActivityId("old"); request.setReplacesRevision(0L);
		var result = service.simulate("operator", request);
		assertEquals(0, new BigDecimal("50").compareTo(result.getBaseline().getWholeDiscount()));
		assertEquals(0, new BigDecimal("20").compareTo(result.getProposed().getWholeDiscount()));
		assertEquals("draft", result.getProposed().getDetails().get(0).getActivityId());
	}

	@Test void replacementPreservesAlreadyConsumedBudget() {
		var existing = activity("old", "50"); existing.setConsumedBudget(new BigDecimal("50"));
		draft.setTotalBudget(new BigDecimal("60"));
		when(commands.get("old")).thenReturn(existing);
		when(activities.selectList(any())).thenReturn(List.of(existing));
		var request = request(); request.setReplacesActivityId("old"); request.setReplacesRevision(0L);
		var result = service.simulate("operator", request);
		assertEquals(0, result.getProposed().getWholeDiscount().signum());
		assertEquals(0, new BigDecimal("50").compareTo(result.getAssumptions().getConsumedBudget()));
	}

	@Test void changedStockOrDraftRevisionExpiresSavedResultWithoutWritingAgain() {
		var result = service.simulate("operator", request());
		facts.getSkus().get(0).setStock(1);
		assertFalse(service.get("operator", result.getId(), true).isCurrent());
		facts.getSkus().get(0).setStock(100);
		draft.setRevision(1L);
		assertFalse(service.get("operator", result.getId(), true).isCurrent());
		assertThrows(RuntimeException.class, () -> service.requireCurrent("operator", result.getId()));
		verify(records, times(1)).insert(any(CampaignSimulationRecord.class));
	}

	@Test void unknownCostStaysUnknownAndLowCostAndStockRisksAreExplicit() {
		facts.getSkus().get(0).setCostPrice(null);
		var result = service.simulate("operator", request());
		assertEquals("UNKNOWN", result.getSkuFacts().get(0).getCostStatus());
		assertNull(result.getSkuFacts().get(0).getCostLower());
		assertTrue(result.getRisks().stream().anyMatch(risk -> "COST_UNKNOWN".equals(risk.getCode())));
		facts.getSkus().get(0).setCostPrice(new BigDecimal("100")); facts.getSkus().get(0).setStock(1);
		result = service.simulate("operator", request());
		assertTrue(result.getRisks().stream().anyMatch(risk -> "BELOW_COST".equals(risk.getCode())));
		assertTrue(result.getRisks().stream().anyMatch(risk -> "STOCK_SHORT".equals(risk.getCode())));
	}

	@Test void expiredOtherActorAndOtherTenantRecordsCannotAuthorizePublish() {
		var result = service.simulate("operator", request());
		assertThrows(RuntimeException.class, () -> service.get("another", result.getId(), true));
		ArynTenantContextHolder.setTenantId("other");
		assertThrows(RuntimeException.class, () -> service.get("operator", result.getId(), true));
		ArynTenantContextHolder.setTenantId("tenant"); saved.get().setExpiresAt(LocalDateTime.now().minusSeconds(1));
		assertFalse(service.get("operator", result.getId(), true).isCurrent());
	}

	@Test void illegalTypeWrongRevisionAndUnauthorizedSampleNeverReachEvaluationOrPersistence() {
		draft.setActivityType("9");
		assertThrows(RuntimeException.class, () -> service.simulate("operator", request()));
		draft.setActivityType("7"); draft.setRevision(1L);
		assertThrows(RuntimeException.class, () -> service.simulate("operator", request()));
		verifyNoInteractions(order, records);
		draft.setRevision(0L); when(order.capture(any(), any())).thenThrow(new IllegalArgumentException("non-member"));
		assertThrows(RuntimeException.class, () -> service.simulate("operator", request()));
		verify(order, never()).compare(any(), any(), any()); verifyNoInteractions(records);
	}

	@Test void futureWindowAndExhaustedBudgetDoNotPretendDraftWasSelected() {
		draft.setStartTime(LocalDateTime.of(2031, 3, 1, 0, 0)); draft.setEndTime(LocalDateTime.of(2031, 4, 1, 0, 0));
		var outside = service.simulate("operator", request());
		assertEquals(0, outside.getProposed().getTotalDiscount().signum());
		assertTrue(outside.getRisks().stream().anyMatch(risk -> "DRAFT_NOT_SELECTED".equals(risk.getCode())));
		draft.setStartTime(LocalDateTime.of(2031, 1, 1, 0, 0)); draft.setTotalBudget(BigDecimal.TEN);
		var exhausted = service.simulate("operator", request());
		assertEquals(0, exhausted.getProposed().getTotalDiscount().signum());
	}

	private CampaignSimulationDTO request() {
		var request = new CampaignSimulationDTO(); request.setDraftActivityId("draft"); request.setExpectedRevision(0L);
		var sample = new PromotionContextDTO(); sample.setTenantId("tenant"); sample.setUserId("buyer"); sample.setPurchaseScene("1");
		sample.setActivityTime(LocalDateTime.of(2031, 2, 1, 0, 0));
		var row = new PromotionContextDTO.SkuItem(); row.setSkuId("sku"); row.setQuantity(2); row.setSalesPrice(new BigDecimal("100"));
		sample.setSkuItems(List.of(row)); request.setSample(sample); return request;
	}
	private PromotionActivity activity(String id, String amount) {
		var activity = new PromotionActivity(); activity.setId(id); activity.setTenantId("tenant"); activity.setRevision(0L);
		activity.setActivityName(id); activity.setActivityType("7"); activity.setScopeType("1");
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED); activity.setStartTime(LocalDateTime.of(2031, 1, 1, 0, 0));
		activity.setEndTime(LocalDateTime.of(2031, 3, 1, 0, 0));
		activity.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":" + amount + "}]}"); return activity;
	}
	private GoodsSku sku() {
		var spu = new GoodsSpu(); spu.setId("spu"); spu.setName("sku");
		return new GoodsSku().setId("sku").setTenantId("tenant").setSalesPrice(new BigDecimal("100"))
				.setCostPrice(new BigDecimal("60")).setStock(100).setGoodsSpu(spu);
	}
	private CampaignOrderComparisonVO comparison(PromotionCalculationVO baseline, PromotionCalculationVO proposed) {
		var comparison = new CampaignOrderComparisonVO(); comparison.setBaseline(quote(baseline)); comparison.setProposed(quote(proposed)); return comparison;
	}
	private CampaignOrderComparisonVO.Quote quote(PromotionCalculationVO calculation) {
		var quote = new CampaignOrderComparisonVO.Quote(); quote.setGoodsPayable(new BigDecimal("200").subtract(calculation.getTotalDiscount()));
		var line = new CampaignOrderComparisonVO.Line(); line.setSkuId("sku"); line.setGoodsPayable(quote.getGoodsPayable());
		quote.setLines(List.of(line)); return quote;
	}
}
