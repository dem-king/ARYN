package com.aryn.cloud.product.api.entity;

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
 * 类目角标配置（租户级）
 *
 * <p>分类页左栏类目名前的「荐」「热」等小方块，历史上是写死在 C 端代码里的枚举
 * （{@code goods_category.badge_type} 只允许 0/1/2）。现在改为运营在管理后台自由配置
 * 文字、底色、字色、排序与启停：{@code goods_category.badge_type} 存的
 * {@link #badgeKey} 再反查本表，新增角标不需要改代码发版。
 *
 * <p>{@link #badgeKey} 是稳定键：新建时由后端生成（初值等于雪花 id），创建后不可修改；
 * 逻辑删除后仍占用唯一键 {@code (tenant_id, badge_key)}，但雪花 id 不会复用，无需特殊处理。
 *
 * @since 2026-11-05 类目角标配置化
 */
@Data
@Schema(description = "类目角标配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("goods_category_badge")
public class GoodsCategoryBadge extends Model<GoodsCategoryBadge> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/**
	 * 稳定键：{@code goods_category.badge_type} 存此值，创建后不可修改。
	 *
	 * <p>试点租户沿用历史值 {@code '1'}/{@code '2'} 兼容存量类目引用；其它租户新建角标时
	 * 由后端生成雪花 id 字符串，避免与历史枚举值语义混淆。
	 */
	@Schema(description = "角标稳定键（goods_category.badge_type 存此值，创建后不可修改）")
	private String badgeKey;

	@Schema(description = "角标文字（1-2 个字符）")
	private String badgeText;

	@Schema(description = "底色（#RRGGBB）")
	private String bgColor;

	@Schema(description = "字色（#RRGGBB）")
	private String textColor;

	/**
	 * 角标图片地址（可空）。
	 *
	 * <p>空串表示文字角标；配置后 C 端优先渲染图片角标（文字仅作后台检索/回显用）。
	 * 图片按 {@link #badgeHeight} 正方形等比缩放，圆角取 {@link #borderRadius}。
	 */
	@Schema(description = "角标图片地址（空串=文字角标；配置后 C 端优先渲染图片）")
	private String iconUrl;

	/** 角标高度（rpx，16–96，默认 28）；文字角标的字号/左右内边距由它推导 */
	@Schema(description = "角标高度（rpx，16-96，默认 28）")
	private Integer badgeHeight;

	/** 角标圆角（rpx，0–48，默认 4） */
	@Schema(description = "角标圆角（rpx，0-48，默认 4）")
	private Integer borderRadius;

	@Schema(description = "排序序号（升序）")
	private Integer sort;

	@Schema(description = "状态：0.启用；1.停用")
	private String status;

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
	@Schema(description = "逻辑删除：0.正常；1.删除")
	private String delFlag;

	@Schema(description = "租户 ID")
	private String tenantId;

}
