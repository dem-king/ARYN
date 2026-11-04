package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客服加固（2026-11-04）新增 SQL 的契约守卫。
 *
 * <p>这些查询承载「历史会话/主管视图/待领取昵称/复合游标/按租户维护/负载对账」等契约语义，
 * 用文本断言锁住关键条件，防止后续改动悄悄退回旧语义。</p>
 */
class ConversationViewMapperContractTest {

	@Test
	void conversationViewColumnsExposeParticipantSnapshotsAndPeerReadCursor() throws IOException {
		String xml = read("src/main/resources/mapper/MessageConversationMapper.xml");

		// 会员/负责客服快照来自两个参与者 LEFT JOIN（JOIN 写在各 select 中），昵称列与对方已读子查询在片段里。
		assertTrue(xml.contains("LEFT JOIN message_participant cp"));
		assertTrue(xml.contains("LEFT JOIN message_participant sp"));
		assertTrue(xml.contains("cp.participant_name AS customer_name"));
		assertTrue(xml.contains("sp.participant_name AS assigned_staff_name"));
		assertTrue(xml.contains("AS peer_last_read_seq"));
		assertTrue(xml.contains("pp.participant_type &lt;&gt; p.participant_type"));
		// 最近活跃排序键同时作为不透明游标的组成。
		assertTrue(xml.contains("COALESCE(c.last_message_time, c.create_time) AS last_active_time"));
		assertTrue(xml.contains("ORDER BY COALESCE(c.last_message_time, c.create_time) DESC, c.id DESC"));
		assertTrue(xml.contains("AND c.id &lt; #{cursorId}"));
	}

	@Test
	void inboxSupportsOpenClosedStatusFilter() throws IOException {
		String xml = read("src/main/resources/mapper/MessageConversationMapper.xml");

		assertTrue(xml.contains("AND c.status = 'CLOSED'"));
		assertTrue(xml.contains("c.status IN ('WAITING', 'ASSIGNED', 'ACTIVE')"));
	}

	@Test
	void waitingPoolReturnsCustomerNicknameSnapshot() throws IOException {
		String xml = read("src/main/resources/mapper/MessageConversationMapper.xml");

		assertTrue(xml.contains("cp.participant_name AS customer_name"));
		assertTrue(xml.contains("c.status = 'WAITING'"));
		assertTrue(xml.contains("c.assigned_staff_id IS NULL"));
	}

	@Test
	void maintenanceQueriesAreTenantBoundAndNeverBypassInterceptor() throws IOException {
		String xml = read("src/main/resources/mapper/MessageConversationMapper.xml");
		String mapper = read("src/main/java/com/aryn/cloud/message/mapper/MessageConversationMapper.java");

		assertTrue(xml.contains("selectInactiveConversationsByTenant"));
		assertTrue(xml.contains("tenant_id = #{tenantId}"));
		assertTrue(xml.contains("AND status IN ('ASSIGNED', 'ACTIVE')"));
		// 新增的按租户维护查询不得标注 @InterceptorIgnore（跨租户扫描只允许历史发现方法使用）。
		String byTenant = mapper.substring(mapper.indexOf("List<MessageConversation> selectInactiveConversationsByTenant"));
		assertFalse(byTenant.contains("@InterceptorIgnore"));
	}

	@Test
	void assignmentLogsAndDispatchRecoveryExposeAuditFields() throws IOException {
		String logXml = read("src/main/resources/mapper/MessageAssignmentLogMapper.xml");
		String dispatchXml = read("src/main/resources/mapper/MessageDispatchTaskMapper.xml");

		assertTrue(logXml.contains("action_type"));
		assertTrue(logXml.contains("from_staff_id"));
		assertTrue(logXml.contains("to_staff_id"));
		assertTrue(logXml.contains("ORDER BY create_time ASC"));
		assertTrue(dispatchXml.contains("selectRecoveryTasksByTenant"));
		assertTrue(dispatchXml.contains("tenant_id = #{tenantId}"));
	}

	@Test
	void agentActiveCountSnapshotCanBeReconciled() throws IOException {
		String mapper = read("src/main/java/com/aryn/cloud/message/mapper/MessageAgentMapper.java");
		String xml = read("src/main/resources/mapper/MessageAgentMapper.xml");

		assertTrue(mapper.contains("selectAllAgents"));
		assertTrue(mapper.contains("updateActiveCount"));
		assertTrue(xml.contains("SET current_active_count = #{activeCount}"));
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(Path.of(relativePath));
	}

}
