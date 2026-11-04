package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageAssignmentLog;
import com.aryn.cloud.message.api.vo.conversation.ConversationAssignmentLogVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MessageAssignmentLog 持久层。 */
@Mapper
public interface MessageAssignmentLogMapper extends BaseMapper<MessageAssignmentLog> {

	/** 主管视图的会话分配日志（按操作时间升序，便于前端时间线展示）。 */
	List<ConversationAssignmentLogVO> selectByConversation(@Param("tenantId") String tenantId,
			@Param("conversationId") String conversationId);

}
