package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** MessageDispatchTask 持久层。 */
@Mapper
public interface MessageDispatchTaskMapper extends BaseMapper<MessageDispatchTask> {

	MessageDispatchTask selectByIdForUpdate(@Param("tenantId") String tenantId, @Param("id") String id);

	List<MessageDispatchTask> selectByMessageIds(@Param("tenantId") String tenantId,
			@Param("messageIds") List<String> messageIds);

	List<MessageDispatchTask> selectByMessageId(@Param("tenantId") String tenantId,
			@Param("messageId") String messageId);

	int resetFailedTasks(@Param("tenantId") String tenantId, @Param("taskIds") List<String> taskIds,
			@Param("operatorId") String operatorId);

	int resetTasksForRepublish(@Param("tenantId") String tenantId, @Param("taskIds") List<String> taskIds,
			@Param("operatorId") String operatorId);

	int cancelPendingTasks(@Param("tenantId") String tenantId, @Param("messageId") String messageId,
			@Param("operatorId") String operatorId);

	int markCanceled(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

	@InterceptorIgnore(tenantLine = "true")
	List<MessageDispatchTask> selectRecoveryTasks(@Param("staleBefore") LocalDateTime staleBefore,
			@Param("limit") int limit);

	/**
	 * 按租户查询心跳超时、需要恢复的分发任务（恢复任务服务使用，显式带 tenantId，不绕过租户拦截器）。
	 */
	List<MessageDispatchTask> selectRecoveryTasksByTenant(@Param("tenantId") String tenantId,
			@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);
}
