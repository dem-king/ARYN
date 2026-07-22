package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxItemVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MessageRecipient 持久层。 */
@Mapper
public interface MessageRecipientMapper extends BaseMapper<MessageRecipient> {

	int insertIgnoreBatch(@Param("records") List<MessageRecipient> records);

	List<NoticeInboxItemVO> selectInbox(@Param("tenantId") String tenantId,
			@Param("recipientType") String recipientType, @Param("recipientId") String recipientId,
			@Param("cursor") String cursor, @Param("fetchSize") int fetchSize);

	NoticeInboxItemVO selectInboxDetail(@Param("tenantId") String tenantId,
			@Param("recipientType") String recipientType, @Param("recipientId") String recipientId,
			@Param("messageId") String messageId);

	long countUnread(@Param("tenantId") String tenantId, @Param("recipientType") String recipientType,
			@Param("recipientId") String recipientId);

	int markRead(@Param("tenantId") String tenantId, @Param("recipientType") String recipientType,
			@Param("recipientId") String recipientId, @Param("messageId") String messageId);

	int markAllRead(@Param("tenantId") String tenantId, @Param("recipientType") String recipientType,
			@Param("recipientId") String recipientId);

	int hide(@Param("tenantId") String tenantId, @Param("recipientType") String recipientType,
			@Param("recipientId") String recipientId, @Param("messageId") String messageId);

	MessageRecipient selectRecipient(@Param("tenantId") String tenantId,
			@Param("recipientType") String recipientType, @Param("recipientId") String recipientId,
			@Param("messageId") String messageId);
}
