package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.enums.MallErrorCodeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.service.impl.OrderPriceComputeService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 快递与商城配送共享的地址快照和运费计算。
 */
@Service
@RequiredArgsConstructor
public class DeliveryCheckoutService {

	@DubboReference
	private final RemoteUserAddressService remoteUserAddressService;

	private final DeliveryAreaService deliveryAreaService;

	private final OrderPriceComputeService orderPriceComputeService;

	public void applyDeliveryAddressAndFreight(String deliveryWay, String addressId, String userId,
			OrderInfo orderInfo, List<OrderItemEntity> orderItems, List<GoodsSku> goodsSkus, boolean freeShipping) {
		if (!requiresAddress(deliveryWay)) {
			return;
		}
		if (!StringUtils.hasText(addressId)) {
			throw addressNotFound();
		}
		UserAddress address = remoteUserAddressService.getById(addressId, userId);
		if (address == null) {
			throw addressNotFound();
		}
		if (MallOrderConstants.DELIVERY_WAY_3.equals(deliveryWay)) {
			deliveryAreaService.requireAvailable(address);
		}
		applyRecipientSnapshot(orderInfo, address);
		orderPriceComputeService.orderFreightHandler(orderInfo, orderItems, goodsSkus, freeShipping);
	}

	private boolean requiresAddress(String deliveryWay) {
		return MallOrderConstants.DELIVERY_WAY_1.equals(deliveryWay)
				|| MallOrderConstants.DELIVERY_WAY_3.equals(deliveryWay);
	}

	private void applyRecipientSnapshot(OrderInfo orderInfo, UserAddress address) {
		orderInfo.setRecipientName(address.getRecipientName());
		orderInfo.setRecipientPhone(address.getTelephone());
		orderInfo.setRecipientProvince(address.getProvinceName());
		orderInfo.setRecipientCity(address.getCityName());
		orderInfo.setRecipientArea(address.getAreaName());
		orderInfo.setRecipientProvinceCode(address.getProvinceCode());
		orderInfo.setRecipientCityCode(address.getCityCode());
		orderInfo.setRecipientAreaCode(address.getAreaCode());
		orderInfo.setRecipientAddress(address.getDetailAddress());
	}

	private ArynBusinessException addressNotFound() {
		return new ArynBusinessException(MallErrorCodeEnum.ERROR_50002.getCode(),
				MallErrorCodeEnum.ERROR_50002.getMsg());
	}

}
