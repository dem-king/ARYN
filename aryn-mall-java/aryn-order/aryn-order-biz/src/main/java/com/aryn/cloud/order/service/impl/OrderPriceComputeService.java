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
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderPriceComputeService {

	@DubboReference
	private final RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	private final RedissonClient redissonClient;

	/** 库存锁 key 前缀 */
	private static final String STOCK_LOCK_PREFIX = "stock:lock:";

	/** 锁等待时间（秒） */
	private static final long LOCK_WAIT_SECONDS = 5L;

	/** 锁自动释放时间（秒） */
	private static final long LOCK_LEASE_SECONDS = 30L;

	public void orderFreightHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			List<GoodsSku> goodsSkuList) {
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
			computeItemPayPrice(orderItemEntity);

		}
		computeOrderPrice(orderInfo, orderItemEntityList);
	}

	public void orderStockHandler(List<GoodsSku> goodsSkuList, List<OrderItemEntity> orderItemEntityList) {

		// 对所有涉及的 SKU 加分布式锁，防止高并发超卖
		List<RLock> locks = new ArrayList<>();
		for (OrderItemEntity orderItem : orderItemEntityList) {
			locks.add(redissonClient.getLock(STOCK_LOCK_PREFIX + orderItem.getSkuId()));
		}
		RLock multiLock = redissonClient.getMultiLock(locks.toArray(new RLock[0]));
		boolean locked = false;
		try {
			locked = multiLock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
			if (!locked) {
				throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
						MallErrorCodeEnum.ERROR_60008.getMsg());
			}

			// 在锁内校验库存是否充足
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

			// 扣减库存（远程服务内部应使用乐观锁 UPDATE ... WHERE stock >= ?）
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
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException(MallErrorCodeEnum.ERROR_60008.getCode(),
					MallErrorCodeEnum.ERROR_60008.getMsg());
		}
		finally {
			if (locked && multiLock.isHeldByCurrentThread()) {
				multiLock.unlock();
			}
		}

	}

	public void computeOrderPrice(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		BigDecimal totalPrice = BigDecimal.ZERO;
		BigDecimal paymentPrice = BigDecimal.ZERO;
		BigDecimal freightPrice = BigDecimal.ZERO;
		BigDecimal couponPrice = BigDecimal.ZERO;

		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			totalPrice = totalPrice.add(orderItemEntity.getTotalPrice());
			freightPrice = freightPrice.add(orderItemEntity.getFreightPrice());
			couponPrice = couponPrice.add(orderItemEntity.getCouponPrice());
			paymentPrice = paymentPrice.add(orderItemEntity.getPaymentPrice());
		}

		orderInfo.setTotalPrice(totalPrice)
			.setPaymentPrice(paymentPrice)
			.setFreightPrice(freightPrice)
			.setCouponPrice(couponPrice);

	}

	public void computeItemPayPrice(OrderItemEntity orderItemEntity) {
		BigDecimal itemRealPrice = orderItemEntity.getTotalPrice().subtract(orderItemEntity.getCouponPrice());

		if (itemRealPrice.compareTo(BigDecimal.ZERO) < 0) {
			itemRealPrice = BigDecimal.ZERO;
		}

		orderItemEntity.setPaymentPrice(itemRealPrice.add(orderItemEntity.getFreightPrice()));

	}

	public void orderCouponHandler(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(orderInfo.getCouponUserId())) {
			return;
		}
		BigDecimal couponTotalAmount = BigDecimal.ZERO;
		BigDecimal totalPrice = BigDecimal.ZERO;
		List<OrderItemEntity> listCouponGoods = null;
		String couponUseRange = MallEventConstants.USE_RANGE_1;
		CouponUserRespVO couponUserRespVO = remoteCouponUserService.getById(orderInfo.getCouponUserId());
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
		couponTotalAmount = this.couponCompute(totalPrice, couponUserRespVO, couponInfo);
		for (OrderItemEntity orderItemEntity : orderItemEntityList) {
			BigDecimal couponPrice = BigDecimal.ZERO;
			if (couponTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
				boolean isComputeCoupon = true;
				if (MallEventConstants.USE_RANGE_2.equals(couponUseRange)) {
					isComputeCoupon = listCouponGoods.stream()
						.anyMatch(a -> a.getSkuId().equals(orderItemEntity.getSkuId()));
				}
				if (isComputeCoupon) {
					BigDecimal oneMoneyScope = orderItemEntity.getTotalPrice()
						.divide(totalPrice, 2, RoundingMode.HALF_EVEN);
					couponPrice = oneMoneyScope.multiply(couponTotalAmount).setScale(2, RoundingMode.HALF_EVEN);
					if (couponPrice.compareTo(orderItemEntity.getTotalPrice()) > 0) {
						couponPrice = orderItemEntity.getTotalPrice();
					}
					couponTotalAmount = couponTotalAmount.subtract(couponPrice);
					totalPrice = totalPrice.subtract(orderItemEntity.getTotalPrice());
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

		AtomicReference<BigDecimal> atomicTotalPrice = new AtomicReference<>(BigDecimal.ZERO);
		orderItemEntities.forEach(orderItem -> {
			Integer quantity = listCouponGoods.stream()
				.filter(tree -> tree.getSkuId().equals(orderItem.getSkuId()))
				.toList()
				.get(0)
				.getBuyQuantity();
			atomicTotalPrice.updateAndGet(
					v -> atomicTotalPrice.get().add(orderItem.getSalesPrice().multiply(BigDecimal.valueOf(quantity))));
		});
		return atomicTotalPrice.get();
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
			orderItemEntity.setPaymentPrice(orderItemEntity.getTotalPrice());
			orderItemEntity.setSpecsInfo(placeOrderSku.getSpecsInfo());
			orderItemEntity.setPicUrl(placeOrderSku.getPicUrl());
			return orderItemEntity;
		}).collect(Collectors.toList());

	}

}
