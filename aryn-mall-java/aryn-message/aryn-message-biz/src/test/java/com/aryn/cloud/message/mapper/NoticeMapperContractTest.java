package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeMapperContractTest {

	@Test
	void recipientWritesAndInboxReadsAreIdentityBound() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageRecipientMapper.xml"));

		assertTrue(xml.contains("INSERT IGNORE INTO message_recipient"));
		assertTrue(xml.contains("message_recipient.recipient_type = #{recipientType}"));
		assertTrue(xml.contains("message_recipient.recipient_id = #{recipientId}"));
		assertTrue(xml.contains("recipient_type = #{recipientType}"));
		assertTrue(xml.contains("recipient_id = #{recipientId}"));
	}

	@Test
	void revokedNoticesAreExcludedFromOrdinaryInboxAndUnreadCount() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageRecipientMapper.xml"));

		assertTrue(count(xml, "message_notice.status = 'PUBLISHED'") >= 2);
		assertTrue(xml.contains("message_recipient.inbox_status = 'VISIBLE'"));
		assertTrue(xml.contains("message_notice.expire_time IS NULL"));
	}

	@Test
	void dispatchRecoveryIgnoresTenantInterceptorOnlyForCrossTenantScan() throws IOException {
		String mapper = Files.readString(Path.of("src/main/java/com/aryn/cloud/message/mapper/MessageDispatchTaskMapper.java"));
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageDispatchTaskMapper.xml"));

		assertTrue(mapper.contains("@InterceptorIgnore(tenantLine = \"true\")"));
		assertTrue(xml.contains("status IN ('PENDING', 'RUNNING')"));
		assertTrue(xml.contains("last_heartbeat_time"));
	}

	private long count(String source, String expected) {
		return source.split(java.util.regex.Pattern.quote(expected), -1).length - 1L;
	}

}
