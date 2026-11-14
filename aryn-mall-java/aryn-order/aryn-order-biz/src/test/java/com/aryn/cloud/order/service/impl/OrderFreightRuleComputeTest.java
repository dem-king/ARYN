package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.constant.FreightConstants;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.FreightRule;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.product.api.constant.ProductConstants;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 订单级运费规则（满额包邮）计算口径。
 *
 * 覆盖三层叠加：独立计费商品 → 门槛规则 → 会员免邮，以及自提/到船恒定免运费。
 */
@ExtendWith(MockitoExtension.class)
class OrderFreightRuleComputeTest {

	private OrderPriceComputeService service;

	@BeforeEach
	void setUp() {
		service = new OrderPriceComputeService(mock(RemoteGoodsSkuService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteCouponUserService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteDiscountService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemoteSeckillService.class),
				mock(com.aryn.cloud.promotion.api.remote.RemotePromotionEngine.class));
	}

	private OrderItemEntity item(String skuId, String spuId, String totalPrice, int quantity) {
		return new OrderItemEntity().setSkuId(skuId)
			.setSpuId(spuId)
			.setBuyQuantity(quantity)
			.setTotalPrice(new BigDecimal(totalPrice))
			.setFreightPrice(BigDecimal.ZERO)
			.setCouponPrice(BigDecimal.ZERO)
			.setMemberDiscountPrice(BigDecimal.ZERO)
			.setPromoPrice(BigDecimal.ZERO)
			.setPaymentPrice(new BigDecimal(totalPrice));
	}

	private GoodsSku sku(String skuId, String spuId, String freightType, String fixedFreight) {
		GoodsSpu spu = new GoodsSpu().setId(spuId).setFreightType(freightType);
		if (fixedFreight != null) {
			spu.setFixedFreightPrice(new BigDecimal(fixedFreight));
		}
		return new GoodsSku().setId(skuId).setSpuId(spuId).setGoodsSpu(spu);
	}

	private FreightRule rule(String threshold, String baseFreight, String maxFreight) {
		FreightRule rule = new FreightRule().setId("rule-1")
			.setRuleName("测试规则")
			.setThresholdAmount(new BigDecimal(threshold))
			.setBaseFreight(new BigDecimal(baseFreight));
		if (maxFreight != null) {
			rule.setMaxFreight(new BigDecimal(maxFreight));
		}
		return rule;
	}

	@Test
	@DisplayName("未达门槛：按规则收一次基础运费，并给出还差多少")
	void chargesBaseFreightWhenBelowThreshold() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "50.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, rule("59.00", "6.00", null));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(order.getFreightBasePrice()).isEqualByComparingTo("6.00");
		assertThat(order.getFreightDiscountPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_NONE);
		assertThat(order.getFreightGap()).isEqualByComparingTo("9.00");
		// 门槛运费分摊到明细，保证订单实付 = 明细实付之和
		assertThat(item.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("56.00");
		assertThat(order.getPaymentPrice()).isEqualByComparingTo("56.00");
	}

	@Test
	@DisplayName("达门槛：运费全免，并记录免掉的运费用于成本核算")
	void waivesFreightWhenThresholdReached() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "60.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, rule("59.00", "6.00", null));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightBasePrice()).isEqualByComparingTo("6.00");
		assertThat(order.getFreightDiscountPrice()).isEqualByComparingTo("6.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_THRESHOLD);
		assertThat(order.getFreightGap()).isNull();
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("60.00");
	}

	@Test
	@DisplayName("门槛基数只算「随订单规则」商品：商品级包邮金额不计入")
	void thresholdBaseOnlyCountsRuleGoods() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity freeItem = item("sku-free", "spu-free", "100.00", 1);
		OrderItemEntity ruleItem = item("sku-2", "spu-2", "50.00", 1);
		GoodsSku freeSku = sku("sku-free", "spu-free", ProductConstants.FREIGHT_TYPE_0, null);
		GoodsSku ruleSku = sku("sku-2", "spu-2", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(freeItem, ruleItem), List.of(freeSku, ruleSku), false,
				rule("59.00", "6.00", null));

		// 100 元包邮商品不凑门槛，只有 50 元的规则商品计入
		assertThat(order.getFreightEligibleAmount()).isEqualByComparingTo("50.00");
		assertThat(order.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(freeItem.getFreightPrice()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("固定运费商品每个 SPU 每单只收一次，不再按件线性叠加")
	void fixedFreightChargedOncePerSpu() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "100.00", 3);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_1, "6.00");

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, null);

		assertThat(order.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("106.00");
	}

	@Test
	@DisplayName("同 SPU 多 SKU 多行：固定运费仍只收一次")
	void fixedFreightChargedOnceAcrossSkuLines() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity line1 = item("sku-a", "spu-1", "50.00", 1);
		OrderItemEntity line2 = item("sku-b", "spu-1", "50.00", 2);
		GoodsSku skuA = sku("sku-a", "spu-1", ProductConstants.FREIGHT_TYPE_1, "6.00");
		GoodsSku skuB = sku("sku-b", "spu-1", ProductConstants.FREIGHT_TYPE_1, "6.00");

		service.orderFreightHandler(order, List.of(line1, line2), List.of(skuA, skuB), false, null);

		assertThat(order.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(order.getPaymentPrice()).isEqualByComparingTo("106.00");
	}

	@Test
	@DisplayName("会员免邮：整单清零，减免额等于减免前运费合计")
	void memberFreeShippingWaivesAll() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity fixedItem = item("sku-1", "spu-1", "100.00", 1);
		OrderItemEntity ruleItem = item("sku-2", "spu-2", "50.00", 1);
		GoodsSku fixedSku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_1, "6.00");
		GoodsSku ruleSku = sku("sku-2", "spu-2", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(fixedItem, ruleItem), List.of(fixedSku, ruleSku), true,
				rule("59.00", "6.00", null));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightBasePrice()).isEqualByComparingTo("12.00");
		assertThat(order.getFreightDiscountPrice()).isEqualByComparingTo("12.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_MEMBER);
		assertThat(fixedItem.getFreightPrice()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("上门自提：不计运费，固定运费商品也不能冒出运费")
	void pickupNeverChargesFreight() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_2);
		OrderItemEntity item = item("sku-1", "spu-1", "100.00", 2);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_1, "6.00");

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, null);

		assertThat(order.getFreightPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_PICKUP);
		assertThat(item.getPaymentPrice()).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("公司配送到船：不计运费，原因为到船配送")
	void vesselDeliveryNeverChargesFreight() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_4);
		OrderItemEntity item = item("sku-1", "spu-1", "100.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, rule("59.00", "6.00", null));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_INTERNAL);
	}

	@Test
	@DisplayName("未配置规则：随订单规则商品不收费，不阻断下单")
	void noRuleChargesNothing() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "10.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, null);

		assertThat(order.getFreightPrice()).isEqualByComparingTo("0.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_NONE);
		assertThat(order.getFreightRuleId()).isNull();
	}

	@Test
	@DisplayName("基础运费封顶：超过封顶按封顶收，明细分摊与订单合计守恒")
	void baseFreightCapped() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "10.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, rule("59.00", "100.00", "20.00"));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("20.00");
		assertThat(order.getPaymentPrice()).isEqualByComparingTo("30.00");
		assertThat(item.getFreightPrice()).isEqualByComparingTo("20.00");
	}

	@Test
	@DisplayName("门槛为 0：视为不设门槛，始终收基础运费")
	void zeroThresholdAlwaysCharges() {
		OrderInfo order = new OrderInfo().setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		OrderItemEntity item = item("sku-1", "spu-1", "1000.00", 1);
		GoodsSku sku = sku("sku-1", "spu-1", ProductConstants.FREIGHT_TYPE_2, null);

		service.orderFreightHandler(order, List.of(item), List.of(sku), false, rule("0", "6.00", null));

		assertThat(order.getFreightPrice()).isEqualByComparingTo("6.00");
		assertThat(order.getFreightFreeReason()).isEqualTo(FreightConstants.REASON_NONE);
		assertThat(order.getFreightGap()).isNull();
	}

}
