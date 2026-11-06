package com.aryn.cloud.message.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-A 会话列表筛选 / waitingSince / 队列统计 SQL 契约守卫。
 *
 * <p>用文本断言锁住关键语义，防止后续改动悄悄退回「只按 status 过滤」或丢掉 LIKE 转义、
 * 未读口径、waiting_since 别名与队列聚合条件。</p>
 */
class ConversationInboxSqlContractTest {

	private static final Path CONVERSATION_XML = Path
		.of("src/main/resources/mapper/MessageConversationMapper.xml");

	@Test
	void keywordIsLikeMatchedOnThreeFieldsWithEscapedWildcards() throws IOException {
		String xml = read(CONVERSATION_XML);

		// 字段范围：会员 ID / 会员昵称快照 / 最后消息摘要；转义符固定 '!'，SQL 侧 ESCAPE '!'。
		assertTrue(xml.contains(
				"c.customer_id LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'"));
		assertTrue(xml.contains(
				"cp.participant_name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'"));
		assertTrue(xml.contains(
				"c.last_message_summary LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'"));
	}

	@Test
	void statusesTakePrecedenceOverLegacyStatusFilter() throws IOException {
		String xml = read(CONVERSATION_XML);

		assertTrue(xml.contains("statuses != null and statuses.size() > 0"));
		assertTrue(xml.contains("<foreach collection=\"statuses\" item=\"statusItem\""));
		// statuses 分支在 status 分支之前，保证「statuses 优先」。
		assertTrue(xml.indexOf("statuses.size() > 0") < xml.indexOf("status != null and status != ''"));
		// 旧页签语义（OPEN=WAITING/ASSIGNED/ACTIVE、CLOSED=已关闭）保留。
		assertTrue(xml.contains("c.status IN ('WAITING', 'ASSIGNED', 'ACTIVE')"));
		assertTrue(xml.contains("AND c.status = 'CLOSED'"));
	}

	@Test
	void preciseFiltersAndUnreadSemanticsAreRenderedInBothViews() throws IOException {
		String xml = read(CONVERSATION_XML);

		assertTrue(xml.contains("AND c.assigned_staff_id = #{assignedStaffId}"));
		assertTrue(xml.contains("AND c.queue_code = #{queueCode}"));
		// staff 本人未读：参与者游标落后且排除已关闭。
		assertTrue(xml.contains("AND c.last_seq > p.last_read_seq"));
		// admin 负责客服未读代理：EXISTS ACTIVE SYS_USER 参与者。
		assertTrue(xml.contains("AND EXISTS ("));
		assertTrue(xml.contains("up.participant_type = 'SYS_USER'"));
		assertTrue(xml.contains("AND c.last_seq > up.last_read_seq"));
		assertTrue(xml.contains("c.status != 'CLOSED'"));
	}

	@Test
	void waitingSinceIsExposedByAllConversationViewQueries() throws IOException {
		String xml = read(CONVERSATION_XML);

		// WAITING 时 = create_time，非 WAITING 为 NULL。
		assertTrue(xml.contains("CASE WHEN c.status = 'WAITING' THEN c.create_time ELSE NULL END AS waiting_since"));
		assertTrue(xml.contains("c.create_time,"));
		// 片段（selectForParticipant/selectInbox/selectUnreadForParticipant 共用）+ selectTenantInbox +
		// selectWaitingPool + 主管上下文 selectForTenant 四处显式列清单，共 4 处文本。
		assertEquals(4, countOccurrences(xml,
				"CASE WHEN c.status = 'WAITING' THEN c.create_time ELSE NULL END AS waiting_since"));
		// 排序键不被 waiting_since 影响。
		assertTrue(xml.contains("ORDER BY COALESCE(c.last_message_time, c.create_time) DESC, c.id DESC"));
	}

	@Test
	void queueStatsAggregatesWaitingConversationsByQueueCode() throws IOException {
		String xml = read(CONVERSATION_XML);
		String mapper = read(Path.of("src/main/java/com/aryn/cloud/message/mapper/MessageConversationMapper.java"));

		assertTrue(mapper.contains("selectWaitingGroups"));
		assertTrue(mapper.contains("List<String> statuses"));
		assertTrue(xml.contains("COUNT(*) AS waiting_count"));
		assertTrue(xml.contains("MIN(c.create_time) AS oldest_waiting_since"));
		assertTrue(xml.contains("GROUP BY c.queue_code"));
		assertTrue(xml.contains("AND c.status = 'WAITING'"));
		assertTrue(xml.contains("AND c.assigned_staff_id IS NULL"));
		assertTrue(xml.contains("AND c.conversation_type = 'CUSTOMER_SERVICE'"));
	}

	@Test
	void waitingSinceIsNotAddedToEntityOnlyQueries() throws IOException {
		String xml = read(CONVERSATION_XML);

		// selectWaitingForTenant 返回实体（MessageConversation），不应消费 waiting_since 别名；
		// 这里守卫「新增列只加在 ConversationVO 查询上」，避免给实体现查询引入未知列别名。
		int entitySelectStart = xml.indexOf("<select id=\"selectWaitingForTenant\"");
		assertTrue(entitySelectStart > 0);
		String entitySelect = xml.substring(entitySelectStart, xml.indexOf("</select>", entitySelectStart));
		assertFalse(entitySelect.contains("waiting_since"));
	}

	private String read(Path path) throws IOException {
		return Files.readString(path);
	}

	private int countOccurrences(String text, String target) {
		int count = 0;
		int index = text.indexOf(target);
		while (index >= 0) {
			count++;
			index = text.indexOf(target, index + target.length());
		}
		return count;
	}

}
