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
 * 企业采购额月度累计（等级成长值）。
 *
 * <p>订单完成事件按主体增量累计（scene=2 按 vessel、scene=3 按 buyer_org）；
 * 等级重算读近 12 期求和比对 {@code enterprise_level.condition_value}，不跨库聚合。
 * 唯一键 (tenant, subjectType, subjectId, period)，upsert 累加见 mapper。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业采购额月度累计")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_growth")
public class EnterpriseGrowth extends Model<EnterpriseGrowth> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "统计月份 yyyy-MM（Asia/Shanghai）")
	private String period;

	@Schema(description = "当月主体已支付采购额累计（元）")
	private BigDecimal consumeAmount;

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
