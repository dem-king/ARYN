package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffDirectMapperContractTest {

	@Test
	void activePrivateConversationUsesStaffPairAndTenant() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageConversationMapper.xml"));

		assertTrue(xml.contains("conversation_type = 'STAFF_DIRECT'"));
		assertTrue(xml.contains("staff_pair_key = #{staffPairKey}"));
		assertTrue(xml.contains("tenant_id = #{tenantId}"));
	}

	@Test
	void noticeReplyAuthorizationUsesExactRecipientIdentity() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageRecipientMapper.xml"));

		assertTrue(xml.contains("recipient_type = #{recipientType}"));
		assertTrue(xml.contains("recipient_id = #{recipientId}"));
		assertTrue(xml.contains("message_id = #{messageId}"));
	}

}
