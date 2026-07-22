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

	int assignWaiting(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("staffId") String staffId);

	List<MessageConversation> selectWaiting(@Param("tenantId") String tenantId,
			@Param("queueCode") String queueCode, @Param("limit") int limit);

	@InterceptorIgnore(tenantLine = "true")
	List<MessageConversation> selectInactiveConversations(@Param("inactiveBefore") LocalDateTime inactiveBefore,
			@Param("limit") int limit);
}
