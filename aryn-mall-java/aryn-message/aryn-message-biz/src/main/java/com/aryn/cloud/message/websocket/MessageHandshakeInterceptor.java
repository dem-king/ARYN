package com.aryn.cloud.message.websocket;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** 从服务端安全上下文建立 WebSocket 身份，拒绝客户端伪造参与者。 */
public class MessageHandshakeInterceptor implements HandshakeInterceptor {

	public static final String ATTR_TENANT_ID = "messageTenantId";
	public static final String ATTR_IDENTITY_TYPE = "messageIdentityType";
	public static final String ATTR_IDENTITY_ID = "messageIdentityId";

	private final DeviceTypeEnum expectedDeviceType;

	public MessageHandshakeInterceptor(DeviceTypeEnum expectedDeviceType) {
		this.expectedDeviceType = expectedDeviceType;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		ArynUser user = SecurityUtils.requireUser(expectedDeviceType);
		attributes.put(ATTR_TENANT_ID, user.getTenantId());
		attributes.put(ATTR_IDENTITY_TYPE,
				expectedDeviceType == DeviceTypeEnum.TOC ? MessageIdentityType.MALL_USER.name()
						: MessageIdentityType.SYS_USER.name());
		attributes.put(ATTR_IDENTITY_ID, user.getUserId());
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// 身份只在握手阶段写入 WebSocket session attributes。
	}

}
