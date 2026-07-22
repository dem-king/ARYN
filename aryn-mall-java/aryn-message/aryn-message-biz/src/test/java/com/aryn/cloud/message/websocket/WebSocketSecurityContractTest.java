package com.aryn.cloud.message.websocket;

import com.aryn.cloud.message.api.dto.push.MessagePushEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketSecurityContractTest {

	@Test
	void handshakeIdentityComesOnlyFromSecurityContext() throws IOException {
		String source = Files.readString(Path.of(
				"src/main/java/com/aryn/cloud/message/websocket/MessageHandshakeInterceptor.java"));

		assertTrue(source.contains("SecurityUtils.requireUser(expectedDeviceType)"));
		assertTrue(source.contains("user.getTenantId()"));
		assertTrue(source.contains("user.getUserId()"));
		assertFalse(source.contains("request.getURI().getQuery"));
	}

	@Test
	void appAndStaffUseSeparateDeviceBoundEndpoints() throws IOException {
		String source = Files.readString(Path.of(
				"src/main/java/com/aryn/cloud/message/websocket/MessageWebSocketConfig.java"));

		assertTrue(source.contains("\"/ws/app\""));
		assertTrue(source.contains("DeviceTypeEnum.TOC"));
		assertTrue(source.contains("\"/ws/staff\""));
		assertTrue(source.contains("DeviceTypeEnum.TOB"));
	}

	@Test
	void pushEventContainsNoMessageBodyFields() {
		Set<String> fields = java.util.Arrays.stream(MessagePushEvent.class.getDeclaredFields()).map(Field::getName)
			.collect(java.util.stream.Collectors.toSet());

		assertFalse(fields.contains("content"));
		assertFalse(fields.contains("payload"));
		assertTrue(fields.contains("messageId"));
		assertTrue(fields.contains("seqNo"));
	}

}
