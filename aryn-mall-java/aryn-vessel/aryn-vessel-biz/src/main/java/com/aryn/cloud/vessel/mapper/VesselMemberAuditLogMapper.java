package com.aryn.cloud.vessel.mapper;

import com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 船舶成员关系审计留痕持久层。
 *
 * <p>只有写入与查询，不提供业务更新/删除：审计行的价值在于「发生过就不可改」。
 *
 * @author aryn
 * @since 2026/10/19
 */
@Mapper
public interface VesselMemberAuditLogMapper extends BaseMapper<VesselMemberAuditLog> {

}
