package com.aryn.cloud.user.dubbo;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.aryn.cloud.user.api.dto.MiniAppSubscribeResult;
import com.aryn.cloud.user.api.remote.RemoteMiniAppGateway;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 用户域内的小程序密钥网关，不创建或绑定 TOC 会员。 */
@Slf4j
@Service
@DubboService
public class RemoteMiniAppGatewayImpl implements RemoteMiniAppGateway {

	@Override
	public String exchangeOpenId(String appId, String jsCode) {
		try {
			return getClient(appId).getUserService().getSessionInfo(jsCode).getOpenid();
		}
		catch (WxErrorException exception) {
			throw new IllegalArgumentException("小程序登录凭证交换失败:" + exception.getError().getErrorMsg(), exception);
		}
	}

	@Override
	public MiniAppSubscribeResult sendSubscribeMessage(String appId, String openId, String templateCode,
			Map<String, String> data, String page) {
		WxMaSubscribeMessage message = new WxMaSubscribeMessage()
			.setToUser(openId)
			.setTemplateId(templateCode)
			.setPage(page);
		data.forEach((name, value) -> message.addData(new WxMaSubscribeMessage.MsgData(name, value)));
		try {
			getClient(appId).getMsgService().sendSubscribeMsg(message);
			return MiniAppSubscribeResult.success();
		}
		catch (WxErrorException exception) {
			int code = exception.getError().getErrorCode();
			String errorCode = String.valueOf(code);
			String errorMessage = exception.getError().getErrorMsg();
			if (code == 43101 || code == 40003 || code == 41030) {
				return MiniAppSubscribeResult.terminal(errorCode, errorMessage);
			}
			return MiniAppSubscribeResult.retryable(errorCode, errorMessage);
		}
		catch (RuntimeException exception) {
			log.warn("微信订阅消息调用异常 appId={}", appId, exception);
			return MiniAppSubscribeResult.retryable("NETWORK", "微信服务暂时不可用");
		}
	}

	protected WxMaService getClient(String appId) {
		return WxMiniAppConfiguration.getMaService(appId);
	}
}
