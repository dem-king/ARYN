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
 * 企业会员等级配置。
 *
 * <p>双轨会员的企业轨等级：按「近 12 个月主体已支付采购额」（enterprise_growth 滚动求和）
 * 分档，主体维度（岸上企业/船舶），与个人会员等级（member_level）互不干扰。
 * 等级重算读 growth 表近 12 期求和比对 condition_value，取满足条件的最高档。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业会员等级")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_level")
public class EnterpriseLevel extends Model<EnterpriseLevel> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "等级名称")
	private String levelName;

	@Schema(description = "等级徽章图")
	private String levelIcon;

	@Schema(description = "排序号（越大等级越高）")
	private Integer sortOrder;

	@Schema(description = "升级门槛：近12个月主体采购额（元）")
	private BigDecimal conditionValue;

	@Schema(description = "状态：0启用 1禁用")
	private String status;

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
