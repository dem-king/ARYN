package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 企业成长值事件流水（幂等台账，2026-11-15 审查修复新增）。
 *
 * <p>订单完成累计（biz_type=1，biz_no=orderId）与退款回扣（biz_type=2，biz_no=refundNo）
 * 先按唯一键 (tenant, biz_type, biz_no) INSERT IGNORE 落台账，插入成功才动 growth，
 * MQ 重复投递不再重复累计/回扣。月度对账 Job 以订单域聚合绝对值覆盖 growth，台账不回填。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Data
@Schema(description = "企业成长值事件流水")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_growth_ledger")
public class EnterpriseGrowthLedger extends Model<EnterpriseGrowthLedger> {

	/** 业务类型：订单完成累计 */
	public static final String BIZ_ORDER_COMPLETE = "1";

	/** 业务类型：退款回扣 */
	public static final String BIZ_REFUND = "2";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "业务类型：1订单完成累计 2退款回扣")
	private String bizType;

	@Schema(description = "业务号：完成=orderId，退款=refundNo（幂等唯一键）")
	private String bizNo;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "归属月份 yyyy-MM（按订单完成时间）")
	private String period;

	@Schema(description = "本次变动金额（元，恒正；方向由 biz_type 决定）")
	private BigDecimal amount;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}