package com.aryn.cloud.message.mapper;

import com.aryn.cloud.message.api.entity.MessageAgent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MessageAgent 持久层。 */
@Mapper
public interface MessageAgentMapper extends BaseMapper<MessageAgent> {

	MessageAgent selectByStaffId(@Param("tenantId") String tenantId, @Param("staffId") String staffId);

	MessageAgent selectByStaffIdForUpdate(@Param("tenantId") String tenantId, @Param("staffId") String staffId);

	List<MessageAgent> selectAssignmentCandidates(@Param("tenantId") String tenantId, @Param("limit") int limit);

	int incrementActiveCount(@Param("tenantId") String tenantId, @Param("staffId") String staffId);

	int decrementActiveCount(@Param("tenantId") String tenantId, @Param("staffId") String staffId);
}
