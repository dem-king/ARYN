package com.aryn.cloud.order.service.impl;

import cn.binarywang.wx.miniapp.bean.shop.request.shipping.OrderKeyBean;
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.PayerBean;
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.ShippingListBean;
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoUploadRequest;
import cn.hutool.core.date.DatePattern;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.user.api.remote.RemoteSecOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderWxDeliveryService {

	/** 微信发货方式：同城配送（公司自有配送员送货上门） */
	private static final int WX_LOGISTICS_TYPE_LOCAL_DELIVERY = 2;

	/** 微信发货模式：统一发货 */
	private static final int WX_DELIVERY_MODE_UNIFIED = 1;

	private final IOrderConfigService orderConfigService;

	@DubboReference
	private final RemoteSecOrderService remoteSecOrderService;

	public void uploadDeliveryInfoOnDeliver(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(orderInfo.getTradeType())
				|| !orderInfo.getTradeType().equals(PayTradeTypeEnum.WX_JSAPI_PAY.getName())
				|| !orderInfo.getPaymentType().equals(PayConstants.PAY_TYPE_1)) {
			return;
		}
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)
				|| !orderConfig.getWxDeliveryStatus().equals(CommonConstants.NORMAL_STATUS)) {
			return;
		}
		if (!orderInfo.getStatus().equals(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())) {
			return;
		}
		WxMaOrderShippingInfoUploadRequest request = buildShippingUploadRequest(orderInfo, orderItemEntityList);
		remoteSecOrderService.uploadShippingInfo(request, orderInfo.getAppId());
	}

	/**
	 * 商城配送/公司内部配送发货信息上传。
	 *
	 * <p>这两类配送由公司自有配送员完成，微信 logistics_type 取 2（同城配送）；
	 * 快递发货仍走 {@link #uploadDeliveryInfoOnDeliver}，自提仍走
	 * {@link #uploadDeliveryInfoOnReceive}，互不影响。
	 */
	public void uploadDeliveryInfoOnTaskShipped(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(orderInfo.getTradeType())
				|| !orderInfo.getTradeType().equals(PayTradeTypeEnum.WX_JSAPI_PAY.getName())
				|| !orderInfo.getPaymentType().equals(PayConstants.PAY_TYPE_1)) {
			return;
		}
		if (!MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
				&& !MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay())) {
			return;
		}
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)
				|| !CommonConstants.NORMAL_STATUS.equals(orderConfig.getWxDeliveryStatus())) {
			return;
		}
		if (!OrderStatusEnum.WAITING_FOR_RECEIPT.getCode().equals(orderInfo.getStatus())) {
			return;
		}
		if (orderItemEntityList == null || orderItemEntityList.isEmpty()) {
			return;
		}
		WxMaOrderShippingInfoUploadRequest request = buildShippingUploadRequest(orderInfo, orderItemEntityList);
		// 公司自有司机配送：微信侧按「同城配送」上报
		request.setLogisticsType(WX_LOGISTICS_TYPE_LOCAL_DELIVERY);
		request.setDeliveryMode(WX_DELIVERY_MODE_UNIFIED);
		request.setIsAllDelivered(Boolean.TRUE);
		remoteSecOrderService.uploadShippingInfo(request, orderInfo.getAppId());
	}

	public void uploadDeliveryInfoOnReceive(OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		if (!StringUtils.hasText(orderInfo.getTradeType())
				|| !orderInfo.getTradeType().equals(PayTradeTypeEnum.WX_JSAPI_PAY.getName())
				|| !orderInfo.getDeliveryWay().equals(MallOrderConstants.DELIVERY_WAY_2)
				|| !orderInfo.getPaymentType().equals(PayConstants.PAY_TYPE_1)) {
			return;
		}
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)
				|| !orderConfig.getWxDeliveryStatus().equals(CommonConstants.NORMAL_STATUS)) {
			return;
		}
		WxMaOrderShippingInfoUploadRequest request = buildShippingUploadRequest(orderInfo, orderItemEntityList);
		request.setDeliveryMode(1);
		remoteSecOrderService.uploadShippingInfo(request, orderInfo.getAppId());
	}

	private WxMaOrderShippingInfoUploadRequest buildShippingUploadRequest(OrderInfo orderInfo,
			List<OrderItemEntity> orderItemEntityList) {
		WxMaOrderShippingInfoUploadRequest request = new WxMaOrderShippingInfoUploadRequest();
		OrderKeyBean orderKeyBean = new OrderKeyBean();
		orderKeyBean.setOrderNumberType(2);
		orderKeyBean.setTransactionId(orderInfo.getTransactionId());
		request.setOrderKey(orderKeyBean);
		request.setLogisticsType(4);
		request.setIsAllDelivered(false);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DatePattern.UTC_MS_WITH_XXX_OFFSET_PATTERN);
		String uploadTime = ZonedDateTime.now().format(formatter);
		request.setUploadTime(uploadTime);

		List<ShippingListBean> shippingListBeanList = new ArrayList<>();
		ShippingListBean shippingListBean = new ShippingListBean();
		String spuName = Optional.ofNullable(orderItemEntityList.get(0).getSpuName())
			.map(name -> name.length() > 120 ? name.substring(0, 120) : name)
			.orElse("");
		shippingListBean.setItemDesc(spuName);
		shippingListBeanList.add(shippingListBean);

		request.setShippingList(shippingListBeanList);
		request.setPayer(new PayerBean(orderInfo.getOpenId()));
		return request;
	}

}
