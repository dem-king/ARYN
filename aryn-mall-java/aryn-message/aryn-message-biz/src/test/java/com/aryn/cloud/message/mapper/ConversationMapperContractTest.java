package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMapperContractTest {

	@Test
	void activeCustomerLookupAndParticipantReadsAreTenantBound() throws IOException {
		String conversation = Files.readString(Path.of("src/main/resources/mapper/MessageConversationMapper.xml"));

		assertTrue(conversation.contains("conversation_type = 'CUSTOMER_SERVICE'"));
		assertTrue(conversation.contains("status IN ('WAITING', 'ASSIGNED', 'ACTIVE')"));
		assertTrue(conversation.contains("p.participant_type = #{participantType}"));
		assertTrue(conversation.contains("p.participant_id = #{participantId}"));
	}

	@Test
	void chatUsesClientIdempotencyAndSequenceCursors() throws IOException {
		String chat = Files.readString(Path.of("src/main/resources/mapper/MessageChatMapper.xml"));

		assertTrue(chat.contains("client_message_id = #{clientMessageId}"));
		assertTrue(chat.contains("seq_no &gt; #{afterSeq}"));
		assertTrue(chat.contains("seq_no &lt; #{beforeSeq}"));
		assertTrue(chat.contains("ORDER BY seq_no ASC"));
		assertTrue(chat.contains("ORDER BY seq_no DESC"));
	}

	@Test
	void readCursorUpdateIsMonotonicAndCappedByConversationSequence() throws IOException {
		String participant = Files.readString(Path.of("src/main/resources/mapper/MessageParticipantMapper.xml"));

		assertTrue(participant.contains("p.last_read_seq &lt; #{lastReadSeq}"));
		assertTrue(participant.contains("#{lastReadSeq} &lt;= c.last_seq"));
		assertTrue(participant.contains("participant_type = #{participantType}"));
		assertTrue(participant.contains("participant_id = #{participantId}"));
	}

}
