package com.aryn.cloud.user.api.remote;

import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoUploadRequest;

/**
 * 微信小程序订单接口
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
public interface RemoteSecOrderService {

	void uploadShippingInfo(WxMaOrderShippingInfoUploadRequest wxMaOrderShippingInfoUploadRequest, String appId);

}
