
package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:28
 */
@Data
@Schema(description = "素材分组")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_material_group")
public class SysMaterialGroup extends Model<SysMaterialGroup> {

	/**
	 * 是否默认分组：1.是
	 */
	public static final String DEFAULT_YES = "1";

	/**
	 * 是否默认分组：0.否
	 */
	public static final String DEFAULT_NO = "0";

	/**
	 * 历史虚拟「未分组」分组 id。
	 *
	 * <p>{@code sys_material.group_id} 的默认值就是 '-1'，分组管理页曾经用一条 id='-1' 的
	 * 假数据渲染「未分组」。现在默认分组已落库为真实记录，写入侧遇到该值一律改判到默认分组，
	 * 保留常量只为识别存量脏值。</p>
	 */
	public static final String LEGACY_UNGROUPED_ID = "-1";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "分组名")
	private String name;

	@Schema(description = "是否默认分组：1.是；0.否")
	private String isDefault;

	@Schema(description = "分组类型:1、图片；2、视频；")
	private String type;

	@Schema(description = "排序")
	private Integer sort;

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

	@Schema(description = "租户id")
	private String tenantId;

}
