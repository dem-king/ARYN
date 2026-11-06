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

	/**
	 * 只补齐为空的昵称/头像快照，已有值不覆盖。
	 *
	 * <p>用于历史会话的会员快照回填：早期版本创建会话时 token 里没有昵称，
	 * {@code participant_name}/{@code participant_avatar} 落库为 NULL，管理端只能回退显示会员 ID。
	 */
	int fillSnapshotIfBlank(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("participantType") String participantType, @Param("participantId") String participantId,
			@Param("participantName") String participantName, @Param("participantAvatar") String participantAvatar);

	List<MessageParticipant> selectActiveParticipants(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId);
}
