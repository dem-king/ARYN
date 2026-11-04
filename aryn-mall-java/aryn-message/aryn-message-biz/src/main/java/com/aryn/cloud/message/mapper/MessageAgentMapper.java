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

	List<String> selectEnabledStaffIds(@Param("tenantId") String tenantId, @Param("limit") int limit);

	/** 租户下全部坐席配置（维护任务校正 current_active_count 快照使用，含已禁用坐席）。 */
	List<MessageAgent> selectAllAgents(@Param("tenantId") String tenantId);

	/** 直接把 current_active_count 校正为真实在办数（维护任务对账使用，不走 +1/-1 增量）。 */
	int updateActiveCount(@Param("tenantId") String tenantId, @Param("staffId") String staffId,
			@Param("activeCount") int activeCount);

}
