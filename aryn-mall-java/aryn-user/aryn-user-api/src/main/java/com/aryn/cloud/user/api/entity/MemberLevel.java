package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级配置
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "会员等级配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_level")
public class MemberLevel extends Model<MemberLevel> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "等级名称")
	private String levelName;

	@Schema(description = "等级图标URL")
	private String levelIcon;

	@Schema(description = "升级条件类型：1-累计消费金额；2-累计积分")
	private String conditionType;

	@Schema(description = "升级条件值")
	private BigDecimal conditionValue;

	@Schema(description = "排序号")
	private Integer sortOrder;

	@Schema(description = "状态：0-启用；1-禁用")
	private String status;

	@Schema(description = "成长值阈值")
	private Integer growthValue;

	@Schema(description = "是否付费会员：0-否；1-是")
	private String isPaid;

	@Schema(description = "开通价格")
	private BigDecimal price;

	@Schema(description = "有效期(月)")
	private Integer duration;

	@Schema(description = "专属折扣(0.80=8折)")
	private BigDecimal exclusiveDiscount;

	@Schema(description = "生日礼包积分")
	private Integer birthdayGiftPoints;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
