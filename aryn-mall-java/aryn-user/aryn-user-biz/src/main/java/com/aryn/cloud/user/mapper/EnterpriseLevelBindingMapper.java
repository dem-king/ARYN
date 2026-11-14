package com.aryn.cloud.user.mapper;

import com.aryn.cloud.user.api.entity.EnterpriseLevelBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * EnterpriseLevelBinding 持久层（主体 → 企业等级绑定）。
 *
 * <p>唯一键 (tenant, subject_type, subject_id)：绑定统一走 {@link #upsert}
 * （ON DUPLICATE KEY UPDATE 覆盖当前等级），全仓唯一 INSERT 点。
 */
@Mapper
public interface EnterpriseLevelBindingMapper extends BaseMapper<EnterpriseLevelBinding> {

	/** 按主体唯一键 upsert 绑定；levelId 为空时清空绑定（回到未定级）。 */
	@Insert("""
			INSERT INTO `enterprise_level_binding`
				(`id`, `subject_type`, `subject_id`, `level_id`, `tenant_id`,
				 `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
			VALUES (#{id}, #{subjectType}, #{subjectId}, #{levelId}, #{tenantId},
					'system', NOW(), 'system', NOW(), '0')
			ON DUPLICATE KEY UPDATE
				`level_id` = VALUES(`level_id`),
				`del_flag` = '0',
				`update_by` = 'system',
				`update_time` = NOW()
			""")
	int upsert(@Param("id") String id, @Param("subjectType") String subjectType,
			@Param("subjectId") String subjectId, @Param("levelId") String levelId,
			@Param("tenantId") String tenantId);

}
