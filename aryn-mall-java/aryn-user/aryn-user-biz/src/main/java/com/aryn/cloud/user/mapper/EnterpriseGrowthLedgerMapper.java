package com.aryn.cloud.user.mapper;

import com.aryn.cloud.user.api.entity.EnterpriseGrowthLedger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * EnterpriseGrowthLedger 持久层（企业成长值幂等台账）。
 *
 * <p>唯一键 (tenant, biz_type, biz_no)：{@link #insertIgnore} 返回 0 表示事件已处理过，
 * 调用方必须跳过 growth 变动（MQ 重复投递幂等）。
 */
@Mapper
public interface EnterpriseGrowthLedgerMapper extends BaseMapper<EnterpriseGrowthLedger> {

	/** 幂等落台账：唯一键冲突静默跳过，返回受影响行数（1=首次处理，0=重复事件）。 */
	@Insert("""
			INSERT IGNORE INTO `enterprise_growth_ledger`
				(`id`, `biz_type`, `biz_no`, `subject_type`, `subject_id`, `period`, `amount`, `tenant_id`,
				 `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`)
			VALUES (#{id}, #{bizType}, #{bizNo}, #{subjectType}, #{subjectId}, #{period}, #{amount}, #{tenantId},
					'system', NOW(), 'system', NOW(), '0')
			""")
	int insertIgnore(@Param("id") String id, @Param("bizType") String bizType, @Param("bizNo") String bizNo,
			@Param("subjectType") String subjectType, @Param("subjectId") String subjectId,
			@Param("period") String period, @Param("amount") BigDecimal amount, @Param("tenantId") String tenantId);

}