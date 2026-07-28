package com.aryn.cloud.user.dubbo;

import cn.binarywang.wx.miniapp.api.WxMaMsgService;
import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.aryn.cloud.user.api.dto.MiniAppSubscribeResult;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteMiniAppGatewayImplTest {

	@Test
	void exchangesOnlyOpenIdAndClassifiesSubscribeErrors() throws Exception {
		WxMaService client = mock(WxMaService.class);
		WxMaUserService userService = mock(WxMaUserService.class);
		WxMaMsgService msgService = mock(WxMaMsgService.class);
		when(client.getUserService()).thenReturn(userService);
		when(client.getMsgService()).thenReturn(msgService);
		WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
		session.setOpenid("openid-1");
		when(userService.getSessionInfo("js-code")).thenReturn(session);
		RemoteMiniAppGatewayImpl gateway = new RemoteMiniAppGatewayImpl() {
			@Override
			protected WxMaService getClient(String appId) {
				return client;
			}
		};

		assertThat(gateway.exchangeOpenId("delivery-app", "js-code")).isEqualTo("openid-1");
		verify(userService).getSessionInfo("js-code");

		org.mockito.Mockito.doThrow(new WxErrorException(WxError.builder()
			.errorCode(43101)
			.errorMsg("用户拒绝订阅")
			.build()))
			.when(msgService).sendSubscribeMsg(any());
		MiniAppSubscribeResult terminal = gateway.sendSubscribeMessage("delivery-app", "openid-1",
			"template-1", Map.of("thing1", "配送任务"), "pages/tasks/detail?id=task-1");
		assertThat(terminal.getStatus()).isEqualTo("TERMINAL");
		assertThat(terminal.getErrorCode()).isEqualTo("43101");
	}
}
