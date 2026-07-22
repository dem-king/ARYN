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

	@InterceptorIgnore(tenantLine = "true")
	List<MessageDispatchTask> selectRecoveryTasks(@Param("staleBefore") LocalDateTime staleBefore,
			@Param("limit") int limit);
}
