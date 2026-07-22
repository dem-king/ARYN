package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MessageParticipant 持久层。 */
@Mapper
public interface MessageParticipantMapper extends BaseMapper<MessageParticipant> {

	MessageParticipant selectActiveParticipant(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId, @Param("participantType") String participantType,
			@Param("participantId") String participantId);

	int insertIgnore(@Param("participant") MessageParticipant participant);

	int upsertActive(@Param("participant") MessageParticipant participant);

	int updateLastReadSeq(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("lastReadSeq") long lastReadSeq);

	int deactivate(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("participantType") String participantType, @Param("participantId") String participantId);

	List<MessageParticipant> selectActiveParticipants(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId);
}
