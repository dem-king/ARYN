package com.aryn.cloud.vessel.mapper;

import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * VesselMember 持久层。
 *
 * <p>除基础 CRUD 外，另有两个「含已移除行」的原生 SQL，用于成员关系复活：
 * 唯一键 {@code uk_vessel_member_unique(tenant_id, vessel_id, user_id)} <b>不含 del_flag</b>，
 * 而移除成员走的是逻辑删除，被移除的行仍占着这个键位。因此「退出/被解绑后重新加入」
 * 若直接 insert 必然撞键（MySQL 1062），只能把历史行就地改回有效。
 * MyBatis-Plus 的 logic-delete 会给自身方法自动追加 {@code del_flag = '0'}，
 * 查不到也改不了被删行，故这两个语句写成显式 SQL。
 */
@Mapper
public interface VesselMemberMapper extends BaseMapper<VesselMember> {

	/** 该 (租户, 船舶, 用户) 是否存在历史行（含已逻辑删除的）。 */
	@Select("SELECT COUNT(*) FROM vessel_member WHERE tenant_id = #{tenantId} AND vessel_id = #{vesselId}"
			+ " AND user_id = #{userId}")
	int countIncludingRemoved(@Param("tenantId") String tenantId, @Param("vesselId") String vesselId,
			@Param("userId") String userId);

	/**
	 * 复活被移除的成员行：仅命中 del_flag='1' 的行，有效行不受影响。
	 *
	 * <p>复活同样要覆盖 {@code bind_source} / {@code bind_reason}：历史行带着上一次的来源，
	 * 不覆盖会让「分享链接加进来 → 被运营回收 → 再从邀请码进来」的来源标签一直停在 SHARE_CART，
	 * 来源标记就失去意义。
	 *
	 * @return 受影响行数；0 表示没有可复活的历史行（调用方应改为新建）
	 */
	@Update("UPDATE vessel_member SET del_flag = '0', status = '1', member_role = #{memberRole},"
			+ " can_edit = #{canEdit}, can_confirm = #{canConfirm}, join_time = #{joinTime},"
			+ " remark = #{remark}, bind_source = #{bindSource}, bind_reason = #{bindReason},"
			+ " update_time = NOW()"
			+ " WHERE tenant_id = #{tenantId} AND vessel_id = #{vesselId} AND user_id = #{userId}"
			+ " AND del_flag = '1'")
	int reviveRemovedMember(@Param("tenantId") String tenantId, @Param("vesselId") String vesselId,
			@Param("userId") String userId, @Param("memberRole") String memberRole,
			@Param("canEdit") String canEdit, @Param("canConfirm") String canConfirm,
			@Param("joinTime") LocalDateTime joinTime, @Param("remark") String remark,
			@Param("bindSource") String bindSource, @Param("bindReason") String bindReason);

}
