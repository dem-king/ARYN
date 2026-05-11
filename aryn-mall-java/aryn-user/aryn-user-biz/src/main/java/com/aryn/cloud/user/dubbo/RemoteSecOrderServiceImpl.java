/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 * 仅供购买并获得正式授权的客户使用，侵权必究。
 */

package com.aryn.cloud.user.dubbo;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoUploadRequest;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.remote.RemoteSecOrderService;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSecOrderServiceImpl implements RemoteSecOrderService {

	@Override
	public void uploadShippingInfo(WxMaOrderShippingInfoUploadRequest wxMaOrderShippingInfoUploadRequest,
			String appId) {
		final WxMaService wxService = WxMiniAppConfiguration.getMaService(appId);

		try {
			wxService.getWxMaOrderShippingService().upload(wxMaOrderShippingInfoUploadRequest);
		}
		catch (WxErrorException e) {
			throw new ArynBusinessException(e.getMessage());
		}
	}

}
