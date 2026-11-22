package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoodsPromotionHintsServiceTest {

	private final PromotionActivityMapper mapper = mock(PromotionActivityMapper.class);
	private final PromoCalendarService calendar = mock(PromoCalendarService.class);
	private final RemoteGoodsSkuService products = mock(RemoteGoodsSkuService.class);
	private final RemoteVesselService vessels = mock(RemoteVesselService.class);
	private final GoodsPromotionHintsService service = new GoodsPromotionHintsService(mapper, calendar);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(service, "goodsSkuService", products);
		ReflectionTestUtils.setField(service, "vesselService", vessels);
		var sku = new GoodsSku();
		sku.setId("sku");
		when(products.listBySpuIds(List.of("spu"))).thenReturn(List.of(sku));
		when(products.getBySkuIds(List.of("sku"))).thenReturn(List.of(sku));
		when(calendar.filterByCalendarWindow(eq("tenant"), any(), any())).thenAnswer(call -> call.getArgument(1));
	}

	@Test
	void guestsSeePublicGiftRulesAndBothVolumeThresholds() {
		var giftSku = new GoodsSku();
		giftSku.setId("gift-sku");
		giftSku.setStock(5);
		giftSku.setGoodsSpu(new GoodsSpu());
		giftSku.getGoodsSpu().setName("花生牛奶");
		when(products.getBySkuIds(List.of("gift-sku"))).thenReturn(List.of(giftSku));
		when(mapper.selectList(any())).thenReturn(List.of(
			activity("gift", "5", "2", "sku", "{\"minAmount\":100,\"gifts\":[{\"skuId\":\"gift-sku\",\"quantity\":2}]}"),
			activity("volume", "7", "2", "sku", "{\"tiers\":[{\"minAmount\":100,\"minQty\":8,\"discountAmount\":10}]}")));

		var hints = service.get("tenant", null, "spu", "sku", "2", "call");

		assertThat(hints.gifts()).singleElement().satisfies(hint ->
			assertThat(hint.gifts()).containsExactly(new GoodsPromotionHintsService.Gift("花生牛奶", 2)));
		assertThat(hints.volumes()).singleElement().satisfies(hint ->
			assertThat(hint.tiers()).containsExactly(new GoodsPromotionHintsService.VolumeTier(
				new BigDecimal("100"), 8, new BigDecimal("10"))));
	}

	@Test
	void onlyVerifiedVesselMembersSeePortScopedRules() {
		when(mapper.selectList(any())).thenReturn(List.of(
			activity("volume", "7", "7", "port", "{\"tiers\":[{\"minQty\":8,\"discountAmount\":10}]}")));
		var call = new VesselContextDTO();
		call.setVesselId("vessel");
		call.setVesselCallId("call");
		call.setPortCode("port");
		when(vessels.getVesselCallContext("tenant", "call")).thenReturn(call);
		when(vessels.isVesselMember("tenant", "vessel", "member")).thenReturn(true);

		assertThat(service.get("tenant", null, "spu", "sku", "2", "call").volumes()).isEmpty();
		assertThat(service.get("tenant", "outsider", "spu", "sku", "2", "call").volumes()).isEmpty();
		assertThat(service.get("tenant", "member", "spu", "sku", "2", "call").volumes()).hasSize(1);
		assertThat(service.get("tenant", "member", "spu", "other-sku", "2", "call").volumes()).isEmpty();
	}

	@Test
	void fullScopeOrderPromotionsAreNotRepeatedOnGoodsDetail() {
		when(mapper.selectList(any())).thenReturn(List.of(
			activity("gift", "5", "1", null, "{\"minAmount\":3000,\"gifts\":[{\"skuId\":\"gift-sku\",\"quantity\":2}]}"),
			activity("volume", "7", "1", null, "{\"tiers\":[{\"minAmount\":100,\"minQty\":8,\"discountAmount\":10}]}")));

		var hints = service.get("tenant", null, "spu", "sku", "2", "call");

		assertThat(hints.gifts()).isEmpty();
		assertThat(hints.volumes()).isEmpty();
	}

	private PromotionActivity activity(String id, String type, String scope, String value, String rules) {
		var activity = new PromotionActivity();
		activity.setId(id);
		activity.setActivityType(type);
		activity.setScopeType(scope);
		activity.setScopeValue(value);
		activity.setRules(rules);
		return activity;
	}
}
