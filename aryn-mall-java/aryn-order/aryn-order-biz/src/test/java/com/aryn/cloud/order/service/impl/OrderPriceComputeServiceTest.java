package com.aryn.cloud.order.service.impl;

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
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceComputeServiceTest {

	@Mock
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Mock
	private RemoteCouponUserService remoteCouponUserService;

	private OrderPriceComputeService service;

	@BeforeEach
	void setUp() {
		service = new OrderPriceComputeService(remoteGoodsSkuService, remoteCouponUserService);
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
