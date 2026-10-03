package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** MessageConversation 持久层。 */
@Mapper
public interface MessageConversationMapper extends BaseMapper<MessageConversation> {

	MessageConversation selectActiveCustomerConversation(@Param("tenantId") String tenantId,
			@Param("customerId") String customerId, @Param("queueCode") String queueCode);

	MessageConversation selectActiveStaffConversation(@Param("tenantId") String tenantId,
			@Param("staffPairKey") String staffPairKey);

	MessageConversation selectRecentlyClosedCustomerConversation(@Param("tenantId") String tenantId,
			@Param("customerId") String customerId, @Param("queueCode") String queueCode,
			@Param("now") LocalDateTime now);

	MessageConversation selectByIdForUpdate(@Param("tenantId") String tenantId, @Param("id") String id);

	ConversationVO selectForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("conversationId") String conversationId);

	List<ConversationVO> selectInbox(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("cursor") String cursor, @Param("fetchSize") int fetchSize);

	/** 本人参与、且最后一次已读之后有新消息的会话，按最新消息倒序。 */
	List<ConversationVO> selectUnreadForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("limit") int limit);

	/** 与 selectUnreadForParticipant 同口径的总数，用于管理端铃铛角标。 */
	long countUnreadForParticipant(@Param("tenantId") String tenantId,
			@Param("participantType") String participantType, @Param("participantId") String participantId);

	/** 共享池待领取会话数；分配条件与 selectWaiting 保持一致。 */
	long countWaiting(@Param("tenantId") String tenantId, @Param("queueCode") String queueCode);

	int assignWaiting(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("staffId") String staffId);

	/**
	 * 重开会话回 WAITING。assigned_staff_id 等字段必须显式置 NULL：
	 * updateById 默认忽略 null 字段，残留旧坐席会让 assignWaiting 的
	 * assigned_staff_id IS NULL 条件永远不成立，会话无法被领取。
	 */
	int reopenToWaiting(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("customerId") String customerId);

	List<MessageConversation> selectWaiting(@Param("tenantId") String tenantId,
			@Param("queueCode") String queueCode, @Param("limit") int limit);

	@InterceptorIgnore(tenantLine = "true")
	List<MessageConversation> selectInactiveConversations(@Param("inactiveBefore") LocalDateTime inactiveBefore,
			@Param("limit") int limit);
}
