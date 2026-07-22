package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentMapperContractTest {

	@Test
	void candidatesUseLoadThenOldestAssignmentOrder() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageAgentMapper.xml"));

		assertTrue(xml.contains("current_active_count &lt; max_active_count"));
		assertTrue(xml.contains("ORDER BY current_active_count ASC, last_assigned_time ASC, id ASC"));
		assertTrue(xml.contains("current_active_count = current_active_count + 1"));
		assertTrue(xml.contains("GREATEST(current_active_count - 1, 0)"));
	}

	@Test
	void claimUsesSingleConditionalConversationUpdate() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageConversationMapper.xml"));

		assertTrue(xml.contains("status = 'WAITING'"));
		assertTrue(xml.contains("assigned_staff_id IS NULL"));
		assertTrue(xml.contains("SET assigned_staff_id = #{staffId}, status = 'ASSIGNED'"));
	}

	@Test
	void staffParticipantCanBeReactivatedAfterTransferOrReopen() throws IOException {
		String xml = Files.readString(Path.of("src/main/resources/mapper/MessageParticipantMapper.xml"));

		assertTrue(xml.contains("ON DUPLICATE KEY UPDATE"));
		assertTrue(xml.contains("participant_status = 'ACTIVE'"));
		assertTrue(xml.contains("participant_status = 'EXITED'"));
	}

}
