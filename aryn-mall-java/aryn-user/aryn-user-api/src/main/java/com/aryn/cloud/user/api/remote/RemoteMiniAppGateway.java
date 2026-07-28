package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.MiniAppSubscribeResult;

import java.util.Map;

/** 小程序凭证交换与订阅消息网关；真实密钥只保留在用户域。 */
public interface RemoteMiniAppGateway {

	String exchangeOpenId(String appId, String jsCode);

	MiniAppSubscribeResult sendSubscribeMessage(String appId, String openId, String templateCode,
			Map<String, String> data, String page);

}
