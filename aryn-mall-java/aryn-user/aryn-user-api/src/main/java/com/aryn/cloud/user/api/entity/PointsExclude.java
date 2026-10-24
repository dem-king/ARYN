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

import java.time.LocalDateTime;

/**
 * 积分排除清单：命中类目/商品不参与积分抵扣与返积分（品类黑名单）。
 *
 * <p>虚拟商品、充值卡券、硬通货等是行业共识禁区（盒马/山姆/京东均有官方黑名单）；
 * 排除规则同时作用于消耗侧（抵扣基数剔除）与发放侧（返积分基数剔除）。
 */
@Data
@Schema(description = "积分排除清单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_exclude")
public class PointsExclude extends Model<PointsExclude> {

	/** 排除类型：类目 */
	public static final String TYPE_CATEGORY = "1";

	/** 排除类型：商品（SPU） */
	public static final String TYPE_GOODS = "2";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "排除类型：1-类目；2-商品")
	private String targetType;

	@Schema(description = "类目ID / 商品（SPU）ID")
	private String targetId;

	@Schema(description = "名称快照（展示用，便于类目/商品改名后仍可读）")
	private String targetName;

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
