package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.remote.RemoteOrderContextService;
import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.remote.*;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderScenarioPricingServiceTest {
	private OrderScenarioPricingService service;
	private RemoteGoodsSkuService goods;
	private RemoteMallUserService users;
	private RemoteVesselService vessels;
	private RemoteOrderContextService orgs;
	private RemoteCouponUserService coupons;
	private RemoteMallZoneSettingService zones;
	private RemoteEnterpriseUserService enterprise;

	@BeforeEach void setup() {
		ArynTenantContextHolder.setTenantId("tenant");
		service = new OrderScenarioPricingService(new OrderPriceComputeService(mock(RemoteGoodsSkuService.class),
				mock(RemoteCouponUserService.class), mock(RemoteDiscountService.class), mock(RemoteSeckillService.class),
				mock(RemotePromotionEngine.class)));
		goods = mock(RemoteGoodsSkuService.class);
		users = mock(RemoteMallUserService.class);
		vessels = mock(RemoteVesselService.class);
		orgs = mock(RemoteOrderContextService.class);
		coupons = mock(RemoteCouponUserService.class);
		zones = mock(RemoteMallZoneSettingService.class);
		enterprise = mock(RemoteEnterpriseUserService.class);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", goods);
		ReflectionTestUtils.setField(service, "remoteMallUserService", users);
		ReflectionTestUtils.setField(service, "remoteVesselService", vessels);
		ReflectionTestUtils.setField(service, "remoteOrderContextService", orgs);
		ReflectionTestUtils.setField(service, "remoteCouponUserService", coupons);
		ReflectionTestUtils.setField(service, "remoteMallZoneSettingService", zones);
		ReflectionTestUtils.setField(service, "remoteEnterpriseUserService", enterprise);
		UserInfoVO user = new UserInfoVO(); user.setId("buyer"); user.setTenantId("tenant"); user.setStatus("0");
		when(users.getUserById("buyer")).thenReturn(user);
		when(goods.getBySkuIds(List.of("sku"))).thenReturn(List.of(sku()));
		MemberBenefitsVO benefit = new MemberBenefitsVO(); benefit.setDiscountRate(new BigDecimal("0.9"));
		when(users.getMemberBenefits("buyer")).thenReturn(benefit);
	}
	@AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@Test void capturesServerPriceAndPureComparisonReusesCouponAndPreservesInputFacts() {
		var request = context(); request.getSkuItems().get(0).setSalesPrice(BigDecimal.ONE);
		when(coupons.getById("owned", "buyer")).thenReturn(coupon());
		OrderScenarioFactsVO facts = service.capture(request, "owned");
		assertEquals(0, new BigDecimal("100").compareTo(facts.getContext().getSkuItems().get(0).getSalesPrice()));
		clearInvocations(goods, users, vessels, orgs, coupons, zones, enterprise);
		var comparison = service.compare(facts, calculation("0"), calculation("20"));
		assertEquals(0, new BigDecimal("170").compareTo(comparison.getBaseline().getGoodsPayable()));
		assertEquals(0, new BigDecimal("150").compareTo(comparison.getProposed().getGoodsPayable()));
		assertEquals(0, new BigDecimal("10").compareTo(comparison.getProposed().getCouponDiscount()));
		assertEquals("0", facts.getCoupon().getStatus());
		assertEquals(0, new BigDecimal("100").compareTo(facts.getSkus().get(0).getSalesPrice()));
		assertEquals(comparison, service.compare(facts, calculation("0"), calculation("20")));
		verifyNoInteractions(goods, users, vessels, orgs, coupons, zones, enterprise);
	}

	@Test void unissuedProposedCouponReplacesOwnedCouponWithoutMutationOrIssuance() {
		when(coupons.getById("owned", "buyer")).thenReturn(coupon());
		var facts = service.capture(context(), "owned");
		var draft = coupon(); draft.setId("draft:package"); draft.getCouponInfo().setAmount(new BigDecimal("15"));
		facts.setProposedCoupon(draft);
		clearInvocations(coupons, goods, users, enterprise, zones);
		var result = service.compare(facts, calculation("0"), calculation("0"));
		assertEquals(0, new BigDecimal("170").compareTo(result.getBaseline().getGoodsPayable()));
		assertEquals(0, new BigDecimal("165").compareTo(result.getProposed().getGoodsPayable()));
		assertEquals(0, new BigDecimal("15").compareTo(result.getProposed().getCouponDiscount()));
		assertEquals("0", draft.getStatus()); assertEquals("0", facts.getCoupon().getStatus());
		verifyNoInteractions(coupons, goods, users, enterprise, zones);
	}

	@Test void futureCouponExpiryUsesScenarioTimeAndReturnsActualRejectionReason() {
		when(coupons.getById("owned", "buyer")).thenReturn(coupon());
		var requested = context(); requested.setActivityTime(LocalDateTime.of(2031, 2, 3, 0, 0));
		var facts = service.capture(requested, "owned");
		var comparison = service.compare(facts, calculation("0"), calculation("20"));
		assertEquals(0, new BigDecimal("160").compareTo(comparison.getProposed().getGoodsPayable()));
		assertNotNull(comparison.getProposed().getCouponReason());
		assertEquals("0", facts.getCoupon().getStatus());
	}

	@Test void membershipIsCheckedBeforeAnyPrivatePriceOrProductFact() {
		var requested = context(); requested.setPurchaseScene("2"); requested.setVesselId("other-vessel");
		assertThrows(RuntimeException.class, () -> service.capture(requested, null));
		verifyNoInteractions(goods, coupons, enterprise);
		requested.setPurchaseScene("3"); requested.setVesselId(null); requested.setBuyerOrgId("other-org");
		assertThrows(RuntimeException.class, () -> service.capture(requested, null));
		verifyNoInteractions(goods, coupons, enterprise);
	}

	@Test void wrongTenantUserAndSkuAreRejectedAndContextIsRestored() {
		var requested = context(); requested.setTenantId("other");
		assertThrows(RuntimeException.class, () -> service.capture(requested, null));
		verifyNoInteractions(users, goods);
		requested.setTenantId("tenant");
		var foreign = sku().setTenantId("other");
		when(goods.getBySkuIds(any())).thenReturn(List.of(foreign));
		assertThrows(RuntimeException.class, () -> service.capture(requested, null));
		assertEquals("tenant", ArynTenantContextHolder.getTenantId());
	}

	@Test void couponOwnershipAndTenantAreVerified() {
		var coupon = coupon(); coupon.setUserId("another");
		when(coupons.getById("owned", "buyer")).thenReturn(coupon);
		assertThrows(RuntimeException.class, () -> service.capture(context(), "owned"));
	}

	@Test void enterpriseSamplesNeverFallbackToPersonalDiscount() {
		var requested = context(); requested.setPurchaseScene("3"); requested.setBuyerOrgId("org");
		when(orgs.isUsableBuyerOrgMember("tenant", "buyer", "org")).thenReturn(true);
		var facts = service.capture(requested, null);
		assertNotNull(facts.getEnterpriseBenefits());
		assertNull(facts.getMemberBenefits());
		var result = service.compare(facts, calculation("0"), calculation("20"));
		assertEquals(0, new BigDecimal("180").compareTo(result.getProposed().getGoodsPayable()));
		verify(users, never()).getMemberBenefits(anyString());
	}

	@Test void floorSettingsAreFrozenAndNoStockIsReserved() {
		var coupon = coupon(); coupon.getCouponInfo().setAmount(new BigDecimal("200"));
		when(coupons.getById("owned", "buyer")).thenReturn(coupon);
		var facts = service.capture(context(), "owned");
		var result = service.compare(facts, calculation("0"), calculation("0"));
		assertEquals(0, new BigDecimal("0.01").compareTo(result.getProposed().getGoodsPayable()));
		verify(goods, never()).reduceStock(any());
		verify(coupons, never()).getById("captured-only", "buyer");
	}

	private PromotionContextDTO context() {
		var context = new PromotionContextDTO(); context.setTenantId("tenant"); context.setUserId("buyer");
		context.setPurchaseScene("1"); context.setActivityTime(LocalDateTime.of(2031, 2, 1, 0, 0));
		var row = new PromotionContextDTO.SkuItem(); row.setSkuId("sku"); row.setQuantity(2);
		context.setSkuItems(List.of(row)); return context;
	}
	private GoodsSku sku() {
		var spu = new GoodsSpu(); spu.setId("spu"); spu.setName("真实商品"); spu.setStatus("1"); spu.setTenantId("tenant");
		return new GoodsSku().setId("sku").setSpuId("spu").setTenantId("tenant").setStatus("0")
				.setSalesPrice(new BigDecimal("100")).setCostPrice(new BigDecimal("60")).setStock(100).setGoodsSpu(spu);
	}
	private CouponUserRespVO coupon() {
		var info = new CouponInfo(); info.setTenantId("tenant"); info.setUseRange("1"); info.setCouponType("1");
		info.setAmount(BigDecimal.TEN); info.setThreshold(new BigDecimal("150"));
		var coupon = new CouponUserRespVO(); coupon.setTenantId("tenant"); coupon.setUserId("buyer");
		coupon.setStatus("0"); coupon.setValidatTime(LocalDateTime.of(2031, 2, 2, 0, 0)); coupon.setCouponInfo(info);
		return coupon;
	}
	private PromotionCalculationVO calculation(String whole) {
		var calculation = new PromotionCalculationVO(); calculation.setWholeDiscount(new BigDecimal(whole));
		calculation.setTotalDiscount(new BigDecimal(whole)); calculation.setLadderOverrides(List.of());
		calculation.setDetails(List.of()); calculation.setGifts(List.of()); return calculation;
	}
}
