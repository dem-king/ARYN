package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.product.api.constant.ProductConstants;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.CouponUserReqDTO;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.api.remote.RemoteDiscountService;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPriceComputeService {

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	@DubboReference
	private final RemoteDiscountService remoteDiscountService;

	@DubboReference
	private final RemoteSeckillService remoteSeckillService;

	@DubboReference
	private final RemotePromotionEngine remotePromotionEngine;

	/**
	 * 促销价格处理：秒杀价 > 限时折扣 > 原价
	 * 在运费计算前应用，使后续会员折扣/优惠券基于促销后价格计算
	 */
	public void orderPromotionPriceHandler(List<OrderItemEntity> orderItemEntityList) {
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal originalUnitPrice = item.getSalesPrice();
			if (originalUnitPrice == null) {
				// 未定价商品不做促销价处理，避免空指针
				continue;
			}
			BigDecimal promotionUnitPrice = null;
			// 优先秒杀价
			try {
				BigDecimal seckillPrice = remoteSeckillService.getSeckillPrice(item.getSkuId());
				if (seckillPrice != null && seckillPrice.compareTo(BigDecimal.ZERO) > 0) {
					promotionUnitPrice = seckillPrice;
				}
			} catch (Exception e) {
				// Dubbo 调用失败不阻断下单，降级用折扣/原价
			}
			// 其次限时折扣
			if (promotionUnitPrice == null) {
				try {
					promotionUnitPrice = remoteDiscountService.calculatePrice(item.getSkuId(), originalUnitPrice);
				} catch (Exception e) {
					promotionUnitPrice = originalUnitPrice;
				}
			}
			if (promotionUnitPrice == null) {
				promotionUnitPrice = originalUnitPrice;
			}
			// 促销价不能高于原价
			if (promotionUnitPrice.compareTo(originalUnitPrice) > 0) {
				promotionUnitPrice = originalUnitPrice;
			}
			item.setSalesPrice(promotionUnitPrice);
			item.setTotalPrice(promotionUnitPrice.multiply(BigDecimal.valueOf(item.getBuyQuantity())));
		}
	}

	public void orderFreightHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList, boolean freeShipping) {
		// 先应用促销价格（秒杀 > 限时折扣 > 原价）
		orderPromotionPriceHandler(orderItemEntityList);
		Map<String, GoodsSku> skuMap = goodsSkuList.stream().collect(Collectors.toMap(GoodsSku::getId, v -> v));

		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			GoodsSku goodsSku = skuMap.get(orderItemEntity.getSkuId());
			GoodsSpu goodsSpu = goodsSku.getGoodsSpu();
			BigDecimal freightPrice = BigDecimal.ZERO;

			if (ProductConstants.FREIGHT_TYPE_1.equals(goodsSpu.getFreightType())) {
				freightPrice = goodsSpu.getFixedFreightPrice()
					.multiply(BigDecimal.valueOf(orderItemEntity.getBuyQuantity()));
				orderItemEntity.setFreightPrice(freightPrice);
			}
			if (freeShipping) {
				orderItemEntity.setFreightPrice(BigDecimal.ZERO);
			}
			computeItemPayPrice(orderItemEntity);

		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public void orderStockHandler(List<GoodsSku> goodsSkuList, List<OrderItemEntity> orderItemEntityList) {

		List<GoodsSku> skuList = goodsSkuList.stream()
			.filter(goodsSku -> goodsSku.getStock() >= orderItemEntityList.stream()
				.filter(skuReq -> skuReq.getSkuId().equals(goodsSku.getId()))
				.findFirst()
				.get()
				.getBuyQuantity())
			.toList();
		if (CollUtil.isEmpty(skuList) || skuList.size() < orderItemEntityList.size()) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}
		boolean result = remoteGoodsSkuService.reduceStock(orderItemEntityList.stream().map(v -> {
			GoodsSkuStockReqDTO goodsSkuStockReqDTO = new GoodsSkuStockReqDTO();
			goodsSkuStockReqDTO.setSkuId(v.getSkuId());
			goodsSkuStockReqDTO.setStockNum(v.getBuyQuantity());
			goodsSkuStockReqDTO.setSpuId(v.getSpuId());
			return goodsSkuStockReqDTO;
		}).toList());
		if (!result) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}

	}

	public void computeOrderPrice(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		BigDecimal totalPrice = BigDecimal.ZERO;
		BigDecimal paymentPrice = BigDecimal.ZERO;
		BigDecimal freightPrice = BigDecimal.ZERO;
		BigDecimal couponPrice = BigDecimal.ZERO;
		BigDecimal memberDiscountPrice = BigDecimal.ZERO;
		BigDecimal promoPrice = BigDecimal.ZERO;

		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			totalPrice = totalPrice.add(orderItemEntity.getTotalPrice());
			freightPrice = freightPrice.add(orderItemEntity.getFreightPrice());
			couponPrice = couponPrice.add(orderItemEntity.getCouponPrice());
			memberDiscountPrice = memberDiscountPrice.add(orderItemEntity.getMemberDiscountPrice());
			promoPrice = promoPrice
				.add(orderItemEntity.getPromoPrice() != null ? orderItemEntity.getPromoPrice() : BigDecimal.ZERO);
			paymentPrice = paymentPrice.add(orderItemEntity.getPaymentPrice());
		}

		orderInfo.setTotalPrice(totalPrice)
			.setPaymentPrice(paymentPrice)
			.setFreightPrice(freightPrice)
			.setCouponPrice(couponPrice)
			.setPromoPrice(promoPrice)
			.setMemberDiscountPrice(memberDiscountPrice);

	}

	public void computeItemPayPrice(OrderItemEntity orderItemEntity) {
		BigDecimal promoPrice = orderItemEntity.getPromoPrice() != null ? orderItemEntity.getPromoPrice()
				: BigDecimal.ZERO;
		BigDecimal itemRealPrice = orderItemEntity.getTotalPrice()
			.subtract(orderItemEntity.getMemberDiscountPrice())
			.subtract(orderItemEntity.getCouponPrice())
			.subtract(promoPrice);

		if (itemRealPrice.compareTo(BigDecimal.ZERO) < 0) {
			itemRealPrice = BigDecimal.ZERO;
		}

		orderItemEntity.setPaymentPrice(itemRealPrice.add(orderItemEntity.getFreightPrice()));

	}

	public void orderMemberBenefitHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			MemberBenefitsVO benefits) {
		BigDecimal discountRate = benefits == null ? BigDecimal.ONE : benefits.getDiscountRate();
		BigDecimal pointsMultiplier = benefits == null || benefits.getPointsMultiplier() == null
				? BigDecimal.ONE : benefits.getPointsMultiplier();
		if (discountRate == null || discountRate.compareTo(BigDecimal.ZERO) <= 0
				|| discountRate.compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("会员折扣配置不合法");
		}
		if (pointsMultiplier.compareTo(BigDecimal.ONE) < 0) {
			throw new ArynBusinessException("会员积分倍率配置不合法");
		}
		orderInfo.setPointsMultiplier(pointsMultiplier);
		for (OrderItemEntity item : orderItemEntityList) {
			BigDecimal discountPrice = item.getTotalPrice().multiply(BigDecimal.ONE.subtract(discountRate))
					.setScale(2, RoundingMode.HALF_UP);
			item.setMemberDiscountPrice(discountPrice);
			computeItemPayPrice(item);
		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public void orderCouponHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(orderInfo.getCouponUserId())) {
			return;
		}
		BigDecimal couponTotalAmount = BigDecimal.ZERO;
		BigDecimal totalPrice = BigDecimal.ZERO;
		List<OrderItemEntity> listCouponGoods = null;
		String couponUseRange = MallEventConstants.USE_RANGE_1;
		CouponUserRespVO couponUserRespVO = remoteCouponUserService.getById(orderInfo.getCouponUserId(), orderInfo.getUserId());
		if (Objects.isNull(couponUserRespVO)) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}
		CouponInfo couponInfo = couponUserRespVO.getCouponInfo();
		couponUseRange = couponInfo.getUseRange();
		if (MallEventConstants.USE_RANGE_2.equals(couponInfo.getUseRange())) {
			List<CouponGoods> couponGoodsList = couponUserRespVO.getCouponGoodsList();
			if (CollUtil.isEmpty(couponGoodsList)) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
						MallErrorCodeEnum.ERROR_60060.getMsg());
			}
			listCouponGoods = orderItemEntityList.stream()
				.map(map -> couponGoodsList.stream()
					.filter(m -> Objects.equals(m.getSpuId(), map.getSpuId()))
					.findFirst()
					.map(m -> {
						return map;
					})
					.orElse(null))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
			totalPrice = this.verifyCoupon(listCouponGoods, couponUserRespVO);
		}
		else {
			totalPrice = this.verifyCoupon(orderItemEntityList, couponUserRespVO);
		}
		couponTotalAmount = this.couponCompute(totalPrice, couponUserRespVO, couponInfo).min(totalPrice);
		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			BigDecimal couponPrice = BigDecimal.ZERO;
			if (couponTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
				boolean isComputeCoupon = true;
				if (MallEventConstants.USE_RANGE_2.equals(couponUseRange)) {
					isComputeCoupon = listCouponGoods.stream()
						.anyMatch(a -> a.getSkuId().equals(orderItemEntity.getSkuId()));
				}
				if (isComputeCoupon) {
					BigDecimal discountedItemPrice = discountedGoodsPrice(orderItemEntity);
					BigDecimal oneMoneyScope = discountedItemPrice
						.divide(totalPrice, 2, RoundingMode.HALF_EVEN);
					couponPrice = oneMoneyScope.multiply(couponTotalAmount).setScale(2, RoundingMode.HALF_EVEN);
					if (couponPrice.compareTo(discountedItemPrice) > 0) {
						couponPrice = discountedItemPrice;
					}
					couponTotalAmount = couponTotalAmount.subtract(couponPrice);
					totalPrice = totalPrice.subtract(discountedItemPrice);
				}
			}
			orderItemEntity.setCouponPrice(couponPrice);
			computeItemPayPrice(orderItemEntity);
		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public BigDecimal verifyCoupon(List<OrderItemEntity> listCouponGoods, CouponUserRespVO couponUserRespVO) {
		List<OrderItemEntity> orderItemEntities = listCouponGoods;
		if (!couponUserRespVO.getStatus().equals(CouponUserStatusEnum.STATUS_0.getCode())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60061.getCode(),
					MallErrorCodeEnum.ERROR_60061.getMsg());
		}
		if (couponUserRespVO.getValidatTime().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}

		return orderItemEntities.stream().map(this::discountedGoodsPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal discountedGoodsPrice(OrderItemEntity item) {
		return item.getTotalPrice().subtract(item.getMemberDiscountPrice());
	}

	public BigDecimal couponCompute(BigDecimal totalPrice, CouponUserRespVO couponUser, CouponInfo couponInfo) {

		if (!couponUser.getStatus().equals(CouponUserStatusEnum.STATUS_0.getCode())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60061.getCode(),
					MallErrorCodeEnum.ERROR_60061.getMsg());
		}
		if (couponUser.getValidatTime().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}

		if (couponInfo.getThreshold().compareTo(BigDecimal.ZERO) > 0
				&& totalPrice.compareTo(couponInfo.getThreshold()) < 0) {
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60060.getCode(),
					MallErrorCodeEnum.ERROR_60060.getMsg());
		}
		couponUser.setStatus(CouponUserStatusEnum.STATUS_3.getCode());

		if (MallEventConstants.COUPON_TYPE_1.equals(couponInfo.getCouponType())) {
			return couponInfo.getAmount();
		}
		else if (MallEventConstants.COUPON_TYPE_2.equals(couponInfo.getCouponType())) {
			BigDecimal discount = couponInfo.getDiscount().divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_EVEN);
			return totalPrice.subtract(totalPrice.multiply(discount));
		}
		else {
			return BigDecimal.ZERO;
		}
	}

	public List<OrderItemEntity> generateOrderItems(List<GoodsSku> goodsSkuList,
			List<CreateOrderSkuReqDTO> skuReqList) {
		return goodsSkuList.stream().map(sku -> {
			BigDecimal salesPrice = sku.getSalesPrice();
			CreateOrderSkuReqDTO placeOrderSku = skuReqList.stream()
				.filter(tree -> tree.getSkuId().equals(sku.getId()))
				.toList()
				.get(0);
			GoodsSpu goodsSpu = sku.getGoodsSpu();
			OrderItemEntity orderItemEntity = new OrderItemEntity();
			orderItemEntity.setBuyQuantity(placeOrderSku.getQuantity());
			orderItemEntity.setSpuName(goodsSpu.getName());
			orderItemEntity.setSpuId(goodsSpu.getId());
			orderItemEntity.setSkuId(sku.getId());
			orderItemEntity.setPicUrl(goodsSpu.getSpuUrls()[0]);
			orderItemEntity.setSalesPrice(salesPrice);
			orderItemEntity.setTotalPrice(
					orderItemEntity.getSalesPrice().multiply(BigDecimal.valueOf(placeOrderSku.getQuantity())));
			orderItemEntity.setFreightPrice(BigDecimal.ZERO);
			orderItemEntity.setCouponPrice(BigDecimal.ZERO);
			orderItemEntity.setMemberDiscountPrice(BigDecimal.ZERO);
			orderItemEntity.setPaymentPrice(orderItemEntity.getTotalPrice());
			orderItemEntity.setSpecsInfo(placeOrderSku.getSpecsInfo());
			orderItemEntity.setPicUrl(placeOrderSku.getPicUrl());
			orderItemEntity.setContributorUserId(placeOrderSku.getContributorUserId());
			orderItemEntity.setMemberRemark(placeOrderSku.getMemberRemark());
			return orderItemEntity;
		}).collect(Collectors.toList());

	}


	/**
	 * 营销试算与阶梯价改价：在会员折扣/优惠券之前调用（改 SKU 成交基价）。
	 * 返回计算结果供券后整单优惠分摊；营销服务不可用时按无优惠处理，不阻断交易。
	 */
	public PromotionCalculationVO applyPromotionLadder(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		try {
			PromotionContextDTO context = buildPromotionContext(orderInfo, orderItemEntityList);
			if (context == null) {
				return null;
			}
			PromotionCalculationVO calculation = remotePromotionEngine.preview(context);
			if (calculation == null || calculation.getLadderOverrides() == null) {
				return calculation;
			}
			for (OrderItemEntity item : orderItemEntityList) {
				calculation.getLadderOverrides().stream()
					.filter(override -> override.getSkuId().equals(item.getSkuId()))
					.findFirst()
					.ifPresent(override -> {
						item.setSalesPrice(override.getUnitPrice());
						item.setTotalPrice(override.getUnitPrice().multiply(BigDecimal.valueOf(item.getBuyQuantity())));
						item.setPaymentPrice(item.getTotalPrice());
					});
			}
			return calculation;
		}
		catch (Exception ex) {
			log.warn("订单[{}]营销试算失败，按无优惠处理", orderInfo.getId(), ex);
			return null;
		}
	}

	/**
	 * 整船/整单优惠分摊：在优惠券之后调用，按可折金额占比分摊到明细（最后一条兜底）。
	 */
	public void applyShipWholeDiscount(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			PromotionCalculationVO calculation) {
		if (calculation == null || calculation.getWholeDiscount() == null
				|| calculation.getWholeDiscount().compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		BigDecimal wholeDiscount = calculation.getWholeDiscount();
		BigDecimal discountableBase = orderItemEntityList.stream()
			.map(this::discountedGoodsPrice)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (discountableBase.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}
		if (wholeDiscount.compareTo(discountableBase) > 0) {
			wholeDiscount = discountableBase;
		}
		BigDecimal remaining = wholeDiscount;
		for (int i = 0; i < orderItemEntityList.size(); i++) {
			OrderItemEntity item = orderItemEntityList.get(i);
			BigDecimal itemBase = discountedGoodsPrice(item);
			BigDecimal promo;
			if (i == orderItemEntityList.size() - 1) {
				promo = remaining;
			}
			else {
				promo = itemBase.divide(discountableBase, 4, RoundingMode.HALF_EVEN)
					.multiply(wholeDiscount)
					.setScale(2, RoundingMode.HALF_EVEN);
			}
			if (promo.compareTo(itemBase) > 0) {
				promo = itemBase;
			}
			if (promo.compareTo(BigDecimal.ZERO) < 0) {
				promo = BigDecimal.ZERO;
			}
			item.setPromoPrice(promo);
			remaining = remaining.subtract(promo);
			computeItemPayPrice(item);
		}
		orderInfo.setPromotionDetails(calculation.getDetails());
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	private PromotionContextDTO buildPromotionContext(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		String tenantId = StringUtils.hasText(orderInfo.getTenantId()) ? orderInfo.getTenantId()
				: ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId) || CollUtil.isEmpty(orderItemEntityList)) {
			return null;
		}
		PromotionContextDTO context = new PromotionContextDTO();
		context.setTenantId(tenantId);
		context.setUserId(orderInfo.getUserId());
		context.setOrderId(orderInfo.getId());
		context.setOrderNo(orderInfo.getOrderNo());
		context.setPurchaseScene(orderInfo.getPurchaseScene());
		context.setVesselId(orderInfo.getVesselId());
		context.setVesselCallId(orderInfo.getVesselCallId());
		context.setPortCode(orderInfo.getPortCode());
		context.setSkuItems(orderItemEntityList.stream().map(item -> {
			PromotionContextDTO.SkuItem skuItem = new PromotionContextDTO.SkuItem();
			skuItem.setSkuId(item.getSkuId());
			skuItem.setQuantity(item.getBuyQuantity());
			skuItem.setSalesPrice(item.getSalesPrice());
			return skuItem;
		}).toList());
		return context;
	}


	/**
	 * 营销锁定：下单时预留（订单+活动幂等）。营销服务不可用时按无优惠继续下单。
	 */
	public PromotionCalculationVO reservePromotion(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		try {
			PromotionContextDTO context = buildPromotionContext(orderInfo, orderItemEntityList);
			if (context == null) {
				return null;
			}
			return remotePromotionEngine.reserve(context);
		}
		catch (Exception ex) {
			log.warn("订单[{}]营销锁定失败，按无优惠继续", orderInfo.getId(), ex);
			return null;
		}
	}

	/**
	 * 营销释放：取消/超时时调用（幂等）。
	 */
	public void releasePromotion(String tenantId, String orderId, String reason) {
		remotePromotionEngine.release(tenantId, orderId, reason);
	}

}
