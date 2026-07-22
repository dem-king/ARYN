package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageChat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MessageChat 持久层。 */
@Mapper
public interface MessageChatMapper extends BaseMapper<MessageChat> {

	MessageChat selectByClientMessageId(@Param("tenantId") String tenantId,
			@Param("senderType") String senderType, @Param("senderId") String senderId,
			@Param("clientMessageId") String clientMessageId);

	List<MessageChat> selectCursorPage(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId, @Param("beforeSeq") Long beforeSeq,
			@Param("afterSeq") Long afterSeq, @Param("fetchSize") int fetchSize);
}
