package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageChannelTask;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 外部消息通道任务持久层。 */
@Mapper
public interface MessageChannelTaskMapper extends BaseMapper<MessageChannelTask> {

	@Insert("""
		INSERT IGNORE INTO message_channel_task
		(id, message_id, recipient_type, recipient_id, channel, template_code, template_params,
		 source_type, source_key, status, retry_count, next_retry_time, tenant_id, create_by,
		 create_time, del_flag)
		VALUES
		(#{id}, #{messageId}, #{recipientType}, #{recipientId}, #{channel}, #{templateCode},
		 #{templateParams}, #{sourceType}, #{sourceKey}, #{status}, #{retryCount}, #{nextRetryTime},
		 #{tenantId}, #{createBy}, #{createTime}, #{delFlag})
		""")
	int insertIgnore(MessageChannelTask task);

	@Update("""
		UPDATE message_channel_task
		SET status = 'SENDING', last_attempt_time = #{attemptTime}, update_time = #{attemptTime}
		WHERE tenant_id = #{tenantId} AND id = #{id} AND del_flag = '0'
		  AND status IN ('PENDING', 'RETRY')
		  AND (next_retry_time IS NULL OR next_retry_time <= #{attemptTime})
		""")
	int markSending(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("attemptTime") LocalDateTime attemptTime);

	@Update("""
		UPDATE message_channel_task
		SET status = 'SUCCESS', error_summary = NULL, next_retry_time = NULL,
		    last_attempt_time = #{attemptTime}, update_time = #{attemptTime}
		WHERE tenant_id = #{tenantId} AND id = #{id} AND del_flag = '0' AND status = 'SENDING'
		""")
	int markSuccess(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("attemptTime") LocalDateTime attemptTime);

	@Update("""
		UPDATE message_channel_task
		SET status = 'TERMINAL', error_summary = #{error}, next_retry_time = NULL,
		    last_attempt_time = #{attemptTime}, update_time = #{attemptTime}
		WHERE tenant_id = #{tenantId} AND id = #{id} AND del_flag = '0' AND status = 'SENDING'
		""")
	int markTerminal(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("error") String error, @Param("attemptTime") LocalDateTime attemptTime);

	@Update("""
		UPDATE message_channel_task
		SET status = 'RETRY', retry_count = #{retryCount}, next_retry_time = #{nextRetryTime},
		    error_summary = #{error}, last_attempt_time = #{attemptTime}, update_time = #{attemptTime}
		WHERE tenant_id = #{tenantId} AND id = #{id} AND del_flag = '0' AND status = 'SENDING'
		""")
	int markRetry(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("retryCount") int retryCount, @Param("nextRetryTime") LocalDateTime nextRetryTime,
			@Param("error") String error, @Param("attemptTime") LocalDateTime attemptTime);

	@Update("""
		UPDATE message_channel_task
		SET status = 'RETRY', next_retry_time = #{now}, error_summary = '发送进程中断，等待恢复',
		    update_time = #{now}
		WHERE del_flag = '0' AND status = 'SENDING' AND last_attempt_time < #{staleBefore}
		""")
	@InterceptorIgnore(tenantLine = "true")
	int resetStaleSending(@Param("staleBefore") LocalDateTime staleBefore, @Param("now") LocalDateTime now);

	@Select("""
		SELECT * FROM message_channel_task
		WHERE del_flag = '0' AND status IN ('PENDING', 'RETRY')
		  AND (next_retry_time IS NULL OR next_retry_time <= #{now})
		ORDER BY COALESCE(next_retry_time, create_time), id
		LIMIT #{limit}
		""")
	@InterceptorIgnore(tenantLine = "true")
	List<MessageChannelTask> selectDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
