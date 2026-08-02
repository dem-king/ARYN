package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.WechatSubscribeMsgDTO;

/**
 * 微信小程序订阅消息远程发送接口。
 * 由 aryn-user-biz 实现（持有 WxMaService 配置），供 aryn-message-biz 通过 Dubbo 调用。
 */
public interface RemoteWechatSubscribeService {

	/**
	 * 发送一条订阅消息。
	 * @param dto 消息内容
	 * @return 微信返回的 msgId；失败返回 null
	 */
	String sendSubscribeMessage(WechatSubscribeMsgDTO dto);
}