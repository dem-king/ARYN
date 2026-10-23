package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.product.api.constant.ProductConstants;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemoteDiscountService;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceComputeServiceTest {

	@Mock
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Mock
	private RemoteCouponUserService remoteCouponUserService;

	@Mock
	private RemoteDiscountService remoteDiscountService;

	@Mock
	private RemoteSeckillService remoteSeckillService;

	private OrderPriceComputeService service;

	@BeforeEach
	void setUp() {
		service = new OrderPriceComputeService(remoteGoodsSkuService, remoteCouponUserService,
				remoteDiscountService, remoteSeckillService,
				mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class));
	}

	@Test
	void memberDiscountIsAppliedBeforeCoupon() {
		OrderInfo order = new OrderInfo().setUserId("user-1").setCouponUserId("coupon-user-1");
		OrderItemEntity item = item("100.00");
		MemberBenefitsVO benefits = new MemberBenefitsVO();
		benefits.setDiscountRate(new BigDecimal("0.80"));

		service.orderMemberBenefitHandler(order, List.of(item), benefits);
		when(remoteCouponUserService.getById("coupon-user-1", "user-1")).thenReturn(coupon("10.00"));
		service.orderCouponHandler(order, List.of(item));

		assertThat(item.getMemberDiscountPrice()).isEqualByComparingTo("20.00");
		assertThat(item.getCouponPrice()).isEqualByComparingTo("10.00");
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("70.00");
		assertThat(order.getMemberDiscountPrice()).isEqualByComparingTo("20.00");
	}

	@Test
	void freeShippingClearsFreightAfterItIsCalculated() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity item = item("100.00").setSkuId("sku-1").setBuyQuantity(2);
		GoodsSpu spu = new GoodsSpu();
		spu.setFreightType(ProductConstants.FREIGHT_TYPE_1);
		spu.setFixedFreightPrice(new BigDecimal("6.00"));
		GoodsSku sku = new GoodsSku().setId("sku-1").setGoodsSpu(spu);

		service.orderFreightHandler(order, List.of(item), List.of(sku), true);

		assertThat(item.getFreightPrice()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("100.00");
		assertThat(order.getFreightPrice()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	/**
	 * 共享购物车按人拆行后，同一 SKU 会出现多条明细（每人一条）。
	 * generateOrderItems 必须以请求行为粒度生成，不能按 SKU 去重后只取第一条，
	 * 否则同款商品的其他成员明细会被静默丢弃。
	 */
	@Test
	void generatesOneOrderItemPerRequestRowForSameSku() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("洗衣粉");
		spu.setSpuUrls(new String[] { "pic-1" });
		GoodsSku sku = new GoodsSku().setId("sku-1").setSalesPrice(new BigDecimal("20.00")).setGoodsSpu(spu);

		CreateOrderSkuReqDTO ownerRow = skuReq("sku-1", "spu-1", 5, "user-owner", "王建国");
		CreateOrderSkuReqDTO memberRow = skuReq("sku-1", "spu-1", 3, "user-member", "李海涛");

		List<OrderItemEntity> items = service.generateOrderItems(List.of(sku), List.of(ownerRow, memberRow));

		assertThat(items).hasSize(2);
		assertThat(items).extracting(OrderItemEntity::getContributorName)
			.containsExactly("王建国", "李海涛");
		assertThat(items).extracting(OrderItemEntity::getBuyQuantity).containsExactly(5, 3);
		assertThat(items).allSatisfy(item -> assertThat(item.getSkuId()).isEqualTo("sku-1"));
	}

	/**
	 * 共享购物车按成员拆分下单的请求行不带 picUrl，历史实现用请求里的 null
	 * 覆盖掉已兜底的 SPU 主图，导致订单明细 pic_url 入库即 NULL、三端订单页全部无图。
	 * 请求缺省时必须回落 SKU 图 / SPU 主图。
	 */
	@Test
	void fallsBackToGoodsPicWhenRequestPicMissing() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("医用酒精 75%");
		spu.setSpuUrls(new String[] { "http://img.example.com/alcohol.jpg" });
		GoodsSku sku = new GoodsSku().setId("sku-1").setSalesPrice(new BigDecimal("216.00")).setGoodsSpu(spu);

		List<OrderItemEntity> items = service.generateOrderItems(List.of(sku),
				List.of(skuReq("sku-1", "spu-1", 6, "user-owner", "王建国")));

		assertThat(items).singleElement()
			.satisfies(item -> assertThat(item.getPicUrl()).isEqualTo("http://img.example.com/alcohol.jpg"));
	}

	/**
	 * 购物车链路会在请求行带上下单时的图片快照，其优先级必须高于商品当前图。
	 */
	@Test
	void requestPicWinsOverGoodsPic() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setSpuUrls(new String[] { "http://img.example.com/current.jpg" });
		GoodsSku sku = new GoodsSku().setId("sku-1").setSalesPrice(new BigDecimal("20.00")).setGoodsSpu(spu);
		CreateOrderSkuReqDTO req = skuReq("sku-1", "spu-1", 1, "user-owner", "王建国");
		req.setPicUrl("http://img.example.com/snapshot.jpg");

		List<OrderItemEntity> items = service.generateOrderItems(List.of(sku), List.of(req));

		assertThat(items).singleElement()
			.satisfies(item -> assertThat(item.getPicUrl()).isEqualTo("http://img.example.com/snapshot.jpg"));
	}

	/**
	 * spu_urls 存量脏数据存在字面量 "[]"（TypeHandler 解析成单元素 ["[]"]），
	 * 且请求/SKU 均无图时不得把 "[]" 当图片地址，也不得因空图数组抛数组越界。
	 */
	@Test
	void treatsBracketPlaceholderAsNoPic() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setSpuUrls(new String[] { "[]" });
		GoodsSku sku = new GoodsSku().setId("sku-1").setSalesPrice(new BigDecimal("128.00")).setGoodsSpu(spu);

		List<OrderItemEntity> items = service.generateOrderItems(List.of(sku),
				List.of(skuReq("sku-1", "spu-1", 5, "user-owner", "王建国")));

		assertThat(items).singleElement().satisfies(item -> assertThat(item.getPicUrl()).isNull());
	}

	/**
	 * 库存校验必须按 SKU 汇总数量后再比对：
	 * 拆行后同一 SKU 有多条明细，历史实现取 findFirst() 的单个数量，
	 * 且以「SKU 去重数 &lt; 明细行数」判定不足，多人同购必然误报库存不足。
	 */
	@Test
	void stockCheckAggregatesQuantityAcrossRows() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		GoodsSku sku = new GoodsSku().setId("sku-1").setGoodsSpu(spu).setStock(10);

		OrderItemEntity first = new OrderItemEntity().setSkuId("sku-1").setSpuId("spu-1").setBuyQuantity(6);
		OrderItemEntity second = new OrderItemEntity().setSkuId("sku-1").setSpuId("spu-1").setBuyQuantity(4);
		when(remoteGoodsSkuService.reduceStock(any())).thenReturn(true);

		// 6 + 4 = 10，恰好等于库存，应通过校验（历史实现会因 1 个 SKU < 2 行而抛错）
		service.orderStockHandler(List.of(sku), List.of(first, second));

		assertThat(sku.getId()).isEqualTo("sku-1");
	}

	@Test
	void stockCheckRejectsWhenAggregatedQuantityExceedsStock() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		GoodsSku sku = new GoodsSku().setId("sku-1").setGoodsSpu(spu).setStock(5);

		OrderItemEntity first = new OrderItemEntity().setSkuId("sku-1").setSpuId("spu-1").setBuyQuantity(3);
		OrderItemEntity second = new OrderItemEntity().setSkuId("sku-1").setSpuId("spu-1").setBuyQuantity(3);

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> service.orderStockHandler(List.of(sku), List.of(first, second)))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class);
	}

	/**
	 * 新人价命中的 SKU 必须跳过限时折扣分支：折扣会无条件覆盖 salesPrice，
	 * 把营销引擎已写入的新人价基价冲掉（实测新人价 19.9 会被折扣价 30 覆盖）。
	 * 折扣服务不应被调用（本用例不为其打桩，多余调用会以严格桩校验暴露）。
	 */
	@Test
	void discountBranchSkipsNewbiePricedSku() {
		OrderItemEntity item = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("19.90"));

		service.orderPromotionPriceHandler(List.of(item), null, java.util.Set.of("sku-1"));

		assertThat(item.getSalesPrice()).isEqualByComparingTo("19.90");
		org.mockito.Mockito.verify(remoteDiscountService, org.mockito.Mockito.never())
			.calculatePrice(any(), any());
	}

	/**
	 * 未命中新人价的普通 SKU 不受保护影响，折扣照常生效。
	 */
	@Test
	void discountBranchStillAppliesToOrdinarySku() {
		OrderItemEntity item = new OrderItemEntity().setSkuId("sku-2").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("40.00"));
		when(remoteDiscountService.calculatePrice("sku-2", new BigDecimal("40.00")))
			.thenReturn(new BigDecimal("32.00"));

		service.orderPromotionPriceHandler(List.of(item), null, java.util.Set.of("sku-1"));

		assertThat(item.getSalesPrice()).isEqualByComparingTo("32.00");
	}

	/**
	 * 秒杀优先于新人价：同一 SKU 既命中秒杀又命中新人价时，秒杀价照旧覆盖。
	 */
	@Test
	void seckillWinsOverNewbiePrice() {
		OrderItemEntity item = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("19.90"));
		when(remoteSeckillService.getSeckillPrice("sku-1")).thenReturn(new BigDecimal("12.00"));

		service.orderPromotionPriceHandler(List.of(item), null, java.util.Set.of("sku-1"));

		assertThat(item.getSalesPrice()).isEqualByComparingTo("12.00");
	}

	/**
	 * 已享限时折扣的 SKU 必须标记为活动价：整船优惠的门槛与分摊都要排除它。
	 */
	@Test
	void activityPlanMarksDiscountPricedSku() {
		OrderItemEntity item = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("40.00"));
		when(remoteDiscountService.calculatePrice("sku-1", new BigDecimal("40.00")))
			.thenReturn(new BigDecimal("30.00"));

		OrderPriceComputeService.PromotionPricePlan plan = service.resolvePromotionPricePlan(List.of(item), null);
		service.applyPromotionPricePlan(List.of(item), java.util.Set.of(), plan);

		assertThat(item.getSalesPrice()).isEqualByComparingTo("30.00");
		assertThat(plan.isActivityPriced("sku-1")).isTrue();
	}

	/**
	 * 整船优惠只分摊给未享活动价的明细：折扣价商品不承接整船优惠。
	 */
	@Test
	void shipWholeDiscountSkipsActivityPricedSku() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity activityItem = item("100.00").setSkuId("sku-1").setSalesPrice(new BigDecimal("100.00"));
		OrderItemEntity normalItem = item("100.00").setSkuId("sku-2");
		when(remoteDiscountService.calculatePrice("sku-1", new BigDecimal("100.00")))
			.thenReturn(new BigDecimal("80.00"));
		OrderPriceComputeService.PromotionPricePlan plan = service
			.resolvePromotionPricePlan(List.of(activityItem), null);

		PromotionCalculationVO calculation = new PromotionCalculationVO();
		calculation.setWholeDiscount(new BigDecimal("30.00"));
		calculation.setLadderOverrides(new java.util.ArrayList<>());
		calculation.setDetails(new java.util.ArrayList<>());
		service.applyShipWholeDiscount(order, List.of(activityItem, normalItem), calculation, plan);

		assertThat(activityItem.getPromoPrice()).isEqualByComparingTo("0.00");
		assertThat(normalItem.getPromoPrice()).isEqualByComparingTo("30.00");
		assertThat(order.getPromoPrice()).isEqualByComparingTo("30.00");
	}

	/**
	 * 阶梯价/新人价由营销引擎覆盖，订单侧分摊也要排除对应 SKU。
	 */
	@Test
	void shipWholeDiscountSkipsEnginePricedSku() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity ladderItem = item("100.00").setSkuId("sku-1");
		OrderItemEntity normalItem = item("100.00").setSkuId("sku-2");
		PromotionCalculationVO calculation = new PromotionCalculationVO();
		calculation.setWholeDiscount(new BigDecimal("30.00"));
		PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
		override.setSkuId("sku-1");
		override.setUnitPrice(new BigDecimal("80.00"));
		calculation.setLadderOverrides(new java.util.ArrayList<>(List.of(override)));
		calculation.setDetails(new java.util.ArrayList<>());

		service.applyShipWholeDiscount(order, List.of(ladderItem, normalItem), calculation, null);

		assertThat(ladderItem.getPromoPrice()).isEqualByComparingTo("0.00");
		assertThat(normalItem.getPromoPrice()).isEqualByComparingTo("30.00");
	}

	/**
	 * 限购硬校验：命中新人价的 SKU 整单合计数量必须为 1；
	 * 共享购物车拆行后同一 SKU 两条明细各 1 件合计 2 件也应拦截。
	 */
	@Test
	void newbieQuantityLimitRejectsAggregatedOverOne() {
		PromotionCalculationVO calculation = newbieCalculation("sku-1");
		OrderItemEntity first = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1);
		OrderItemEntity second = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1);

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> service.assertNewbieQuantityLimit(calculation, List.of(first, second)))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class)
			// ArynBusinessException 的真实原因在 msg 字段，getMessage() 为 null
			.satisfies(ex -> assertThat(((com.aryn.cloud.common.security.handler.ArynBusinessException) ex).getMsg())
				.contains("每人限购 1 件"));
	}

	@Test
	void newbieQuantityLimitPassesAtExactlyOne() {
		PromotionCalculationVO calculation = newbieCalculation("sku-1");
		OrderItemEntity item = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1);
		OrderItemEntity other = new OrderItemEntity().setSkuId("sku-2").setBuyQuantity(5);

		// 非新人价 SKU 数量不受限
		service.assertNewbieQuantityLimit(calculation, List.of(item, other));
	}

	/**
	 * 第二次 preview（reserve）必须按 SKU 原价重建上下文：
	 * 下单前 applyPromotionLadder 已把阶梯价写进明细 salesPrice，
	 * 若沿用改后价，活动价等于基价会判为未命中，锁与预算都不会落库。
	 */
	@Test
	void reservePromotionRebuildsContextWithOriginalSkuPrice() {
		com.aryn.cloud.promotion.api.remote.RemotePromotionEngine remoteEngine
			= mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class);
		OrderPriceComputeService local = new OrderPriceComputeService(remoteGoodsSkuService, remoteCouponUserService,
				remoteDiscountService, remoteSeckillService, remoteEngine);
		PromotionCalculationVO reserved = new PromotionCalculationVO();
		reserved.setTotalDiscount(new BigDecimal("60.00"));
		when(remoteEngine.reserve(any())).thenReturn(reserved);

		OrderInfo order = new OrderInfo().setId("order-1").setTenantId("tenant-1");
		// 明细价已被阶梯价改成 24，原价应为 30
		OrderItemEntity orderItem = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(10)
			.setSalesPrice(new BigDecimal("24.00"));
		GoodsSku sku = new GoodsSku().setId("sku-1").setSalesPrice(new BigDecimal("30.00"));
		PromotionCalculationVO expected = new PromotionCalculationVO();
		expected.setTotalDiscount(new BigDecimal("60.00"));

		local.reservePromotion(order, List.of(orderItem), List.of(sku), expected);

		org.mockito.ArgumentCaptor<com.aryn.cloud.promotion.api.dto.PromotionContextDTO> captor
			= org.mockito.ArgumentCaptor.forClass(com.aryn.cloud.promotion.api.dto.PromotionContextDTO.class);
		org.mockito.Mockito.verify(remoteEngine).reserve(captor.capture());
		assertThat(captor.getValue().getSkuItems()).singleElement()
			.satisfies(item -> assertThat(item.getSalesPrice()).isEqualByComparingTo("30.00"));
	}

	/** 试算与锁定让利额不一致（额度被并发抢完等）时拒绝成单，避免静默涨价。 */
	@Test
	void reservePromotionRejectsWhenDiscountChanged() {
		com.aryn.cloud.promotion.api.remote.RemotePromotionEngine remoteEngine
			= mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class);
		OrderPriceComputeService local = new OrderPriceComputeService(remoteGoodsSkuService, remoteCouponUserService,
				remoteDiscountService, remoteSeckillService, remoteEngine);
		PromotionCalculationVO reserved = new PromotionCalculationVO();
		reserved.setTotalDiscount(BigDecimal.ZERO);
		when(remoteEngine.reserve(any())).thenReturn(reserved);

		OrderInfo order = new OrderInfo().setId("order-1").setTenantId("tenant-1");
		OrderItemEntity orderItem = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("30.00"));
		PromotionCalculationVO expected = new PromotionCalculationVO();
		expected.setTotalDiscount(new BigDecimal("10.00"));

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> local.reservePromotion(order, List.of(orderItem), List.of(), expected))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class)
			.satisfies(ex -> assertThat(
					((com.aryn.cloud.common.security.handler.ArynBusinessException) ex).getMsg())
				.contains("优惠已变更"));
	}

	/** 锁定服务不可用但下单已按优惠价算价：拒绝成单，避免「优惠价成交却没有锁」。 */
	@Test
	void reservePromotionRejectsFreeDiscountWhenReserveUnavailable() {
		com.aryn.cloud.promotion.api.remote.RemotePromotionEngine remoteEngine
			= mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class);
		OrderPriceComputeService local = new OrderPriceComputeService(remoteGoodsSkuService, remoteCouponUserService,
				remoteDiscountService, remoteSeckillService, remoteEngine);
		when(remoteEngine.reserve(any())).thenThrow(new RuntimeException("dubbo timeout"));

		OrderInfo order = new OrderInfo().setId("order-1").setTenantId("tenant-1");
		OrderItemEntity orderItem = new OrderItemEntity().setSkuId("sku-1").setBuyQuantity(1)
			.setSalesPrice(new BigDecimal("30.00"));
		PromotionCalculationVO expected = new PromotionCalculationVO();
		expected.setTotalDiscount(new BigDecimal("10.00"));

		org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> local.reservePromotion(order, List.of(orderItem), List.of(), expected))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class)
			.satisfies(ex -> assertThat(
					((com.aryn.cloud.common.security.handler.ArynBusinessException) ex).getMsg())
				.contains("优惠服务暂不可用"));
	}

	private PromotionCalculationVO newbieCalculation(String skuId) {
		PromotionCalculationVO calculation = new PromotionCalculationVO();
		PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
		override.setSkuId(skuId);
		override.setUnitPrice(new BigDecimal("19.90"));
		override.setOriginalPrice(new BigDecimal("28.50"));
		override.setActivityId("act-newbie");
		override.setActivityType(com.aryn.cloud.promotion.api.entity.PromotionActivity.TYPE_NEWBIE_PRICE);
		calculation.setLadderOverrides(new java.util.ArrayList<>(List.of(override)));
		return calculation;
	}

	private CreateOrderSkuReqDTO skuReq(String skuId, String spuId, int quantity, String contributorId, String name) {
		CreateOrderSkuReqDTO req = new CreateOrderSkuReqDTO();
		req.setSkuId(skuId);
		req.setSpuId(spuId);
		req.setQuantity(quantity);
		req.setContributorUserId(contributorId);
		req.setContributorName(name);
		return req;
	}

	/**
	 * 积分抵现：抵扣基数为商品应付（不含运费），按明细比例分摊，订单与明细金额同步下降。
	 */
	@Test
	void pointsDeductionAllocatesAcrossItemsAndUpdatesPayable() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity first = item("100.00").setSkuId("sku-1")
			.setFreightPrice(new BigDecimal("10.00"))
			.setPaymentPrice(new BigDecimal("110.00"));
		OrderItemEntity second = item("50.00").setSkuId("sku-2");

		service.orderPointsDeductHandler(order, List.of(first, second), 1000, false, pointsContext(5000, 100),
				false);

		assertThat(order.getPointsUsed()).isEqualTo(1000);
		assertThat(order.getPointsDeductPrice()).isEqualByComparingTo("10.00");
		assertThat(order.getPaymentPrice()).isEqualByComparingTo("150.00");
		assertThat(order.getPointsDeductMax()).isEqualTo(3000);
		assertThat(order.getPointsDeductMaxPrice()).isEqualByComparingTo("30.00");
		assertThat(first.getPointsPrice()).isEqualByComparingTo("6.67");
		assertThat(second.getPointsPrice()).isEqualByComparingTo("3.33");
		assertThat(first.getPaymentPrice()).isEqualByComparingTo("103.33");
		assertThat(second.getPaymentPrice()).isEqualByComparingTo("46.67");
	}

	/**
	 * 下单严格模式：请求积分超过可用额度（余额或单笔上限）时拒绝，防止确认页金额与实际扣款不一致。
	 */
	@Test
	void pointsDeductionStrictModeRejectsWhenRequestedExceedsCap() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity item = item("100.00");

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.orderPointsDeductHandler(order, List.of(item), 3000, false,
					pointsContext(5000, 100), true))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class);
	}

	/**
	 * 积分抵现未启用时不改变应付金额，仅返回 0 额度。
	 */
	@Test
	void pointsDeductionDisabledLeavesAmountUnchanged() {
		OrderInfo order = new OrderInfo();
		OrderItemEntity item = item("100.00");
		PointsDeductContextVO context = new PointsDeductContextVO();
		context.setEnabled(false);
		context.setAvailablePoints(5000);
		context.setPointsPerYuan(0);

		service.orderPointsDeductHandler(order, List.of(item), 1000, false, context, false);

		assertThat(order.getPointsUsed()).isZero();
		assertThat(order.getPointsDeductPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getPointsDeductMax()).isZero();
		assertThat(order.getPaymentPrice()).isEqualByComparingTo("100.00");
	}

	/**
	 * 分摊守恒：抵扣金额必须被完整、准确地分摊到明细。
	 *
	 * <p>旧实现按末位明细兜底并在越界时 clamp，这里 100 条 1.00 元明细 / 抵扣 19.50
	 * 会让前 99 条按 HALF_EVEN 各分摊 0.20、末位剩余为负被归零，合计 19.80 反而超额。
	 */
	@Test
	void pointsDeductionAllocationSumsToDeductedAmount() {
		OrderInfo order = new OrderInfo();
		List<OrderItemEntity> items = new java.util.ArrayList<>();
		for (int index = 0; index < 100; index++) {
			items.add(item("1.00").setSkuId("sku-" + index));
		}

		service.orderPointsDeductHandler(order, items, 1950, false, pointsContext(5000, 100), false);

		assertThat(order.getPointsUsed()).isEqualTo(1950);
		assertThat(order.getPointsDeductPrice()).isEqualByComparingTo("19.50");
		BigDecimal allocated = items.stream()
			.map(OrderItemEntity::getPointsPrice)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(allocated).isEqualByComparingTo("19.50");
		assertThat(items.stream()
			.allMatch(orderItem -> orderItem.getPaymentPrice().compareTo(BigDecimal.ZERO) >= 0)).isTrue();
	}

	/**
	 * 船供采购使用 B 端成本价，不允许叠加积分抵现（仅海员个人购买可抵）。
	 */
	@Test
	void pointsDeductionRejectedForShipSupplyPurchase() {
		OrderInfo order = new OrderInfo().setPurchaseScene("2");
		OrderItemEntity orderItem = item("100.00");

		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> service.orderPointsDeductHandler(order, List.of(orderItem), 1000, false,
					pointsContext(5000, 100), true))
			.isInstanceOf(com.aryn.cloud.common.security.handler.ArynBusinessException.class)
			.satisfies(exception -> assertThat(
					((com.aryn.cloud.common.security.handler.ArynBusinessException) exception).getMsg())
				.contains("船供采购订单不支持积分抵现"));
	}

	private PointsDeductContextVO pointsContext(int availablePoints, int pointsPerYuan) {
		PointsDeductContextVO context = new PointsDeductContextVO();
		context.setEnabled(true);
		context.setAvailablePoints(availablePoints);
		context.setPointsPerYuan(pointsPerYuan);
		return context;
	}

	private OrderItemEntity item(String totalPrice) {
		return new OrderItemEntity()
				.setTotalPrice(new BigDecimal(totalPrice))
				.setCouponPrice(BigDecimal.ZERO)
				.setMemberDiscountPrice(BigDecimal.ZERO)
				.setFreightPrice(BigDecimal.ZERO)
				.setPaymentPrice(new BigDecimal(totalPrice));
	}

	private CouponUserRespVO coupon(String amount) {
		CouponInfo couponInfo = new CouponInfo();
		couponInfo.setUseRange(MallEventConstants.USE_RANGE_1);
		couponInfo.setCouponType(MallEventConstants.COUPON_TYPE_1);
		couponInfo.setThreshold(BigDecimal.ZERO);
		couponInfo.setAmount(new BigDecimal(amount));
		CouponUserRespVO coupon = new CouponUserRespVO();
		coupon.setStatus(CouponUserStatusEnum.STATUS_0.getCode());
		coupon.setValidatTime(LocalDateTime.now().plusDays(1));
		coupon.setCouponInfo(couponInfo);
		return coupon;
	}

}
