package com.aryn.cloud.user.mapper;

import com.aryn.cloud.user.api.entity.EnterpriseGrowth;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * EnterpriseGrowth 持久层（企业采购额月度累计）。
 *
 * <p>唯一键 (tenant, subject_type, subject_id, period)：累计统一走 {@link #accumulate}
 * （ON DUPLICATE KEY UPDATE 原子累加），全仓唯一 INSERT 点。
 */
@Mapper
public interface EnterpriseGrowthMapper extends BaseMapper<EnterpriseGrowth> {

	/** 按月度唯一键原子累加采购额；行不存在则插入。 */
	@Insert("""
			INSERT INTO `enterprise_growth`
				(`id`, `subject_type`, `subject_id`, `period`, `consume_amount`, `tenant_id`,
				 `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
			VALUES (#{id}, #{subjectType}, #{subjectId}, #{period}, #{amount}, #{tenantId},
					'system', NOW(), 'system', NOW(), '0')
			ON DUPLICATE KEY UPDATE
				`consume_amount` = `consume_amount` + VALUES(`consume_amount`),
				`update_by` = 'system',
				`update_time` = NOW()
			""")
	int accumulate(@Param("id") String id, @Param("subjectType") String subjectType,
			@Param("subjectId") String subjectId, @Param("period") String period,
			@Param("amount") BigDecimal amount, @Param("tenantId") String tenantId);

}
