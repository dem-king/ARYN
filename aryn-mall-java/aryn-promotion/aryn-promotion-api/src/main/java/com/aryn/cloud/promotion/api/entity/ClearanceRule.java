package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出清时间分层规则：距 clearance_expire_time 剩余 ≤N 小时时，在基础出清价上再打折。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("clearance_rule")
public class ClearanceRule extends Model<ClearanceRule> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String tenantId;

	private String ruleName;

	/** 距到期小时数：剩余时间 ≤ 该值时命中 */
	private Integer hoursBeforeExpire;

	/** 在基础出清价上再打折的比率，如 0.8000 */
	private BigDecimal discountRate;

	/** 启用：1是 0否 */
	private String enabled;

	private Integer sortOrder;

	private String remark;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
