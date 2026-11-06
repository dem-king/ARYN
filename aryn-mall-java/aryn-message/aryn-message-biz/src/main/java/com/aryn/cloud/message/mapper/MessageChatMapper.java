package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageChat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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

	/**
	 * 按会话 + 消息 ID 批量查询（引用目标校验与 page 引用摘要批量补全共用，单次 IN 查询避免 N+1）。
	 *
	 * <p>限定同一会话：引用目标只允许同会话消息；查不到的历史脏数据按「引用不可用」降级。</p>
	 */
	List<MessageChat> selectByConversationAndIds(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId, @Param("messageIds") Collection<String> messageIds);

	/**
	 * 条件撤回：仅「发送者本人 + 未撤回 + 未删除」的消息会被更新，返回受影响行数。
	 *
	 * <p>条件更新天然幂等：并发双击第二次返回 0，由服务层按「已撤回」返回成功。</p>
	 */
	int markRecalled(@Param("tenantId") String tenantId, @Param("conversationId") String conversationId,
			@Param("messageId") String messageId, @Param("senderType") String senderType,
			@Param("senderId") String senderId, @Param("recallTime") LocalDateTime recallTime);

}
