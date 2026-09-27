
package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 商品类目
 *
 * @author 雨滴kian
 * @since 2022/2/23 14:10
 */
@Data
@Schema(description = "商品类目")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "goods_category")
public class GoodsCategory extends Model<GoodsCategory> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "类目名称")
	private String name;

	@Schema(description = "上级类目（0.顶级类目）")
	private String parentId;

	@Schema(description = "类目图片")
	private String categoryPic;

	@Schema(description = "类目描述")
	private String description;

	@Schema(description = "状态：0.正常；1.停用；")
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
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "排序序号")
	private Integer sort;

	/**
	 * 类目角标：0.无 1.推荐 2.热卖
	 *
	 * <p>分类页左栏在类目名前渲染一个小图标（参考图的「荐」「热」方块）。
	 * 只存枚举值不存图片地址：样式由 C 端内置，运营只能选语义，
	 * 避免同一语义在不同租户下配色漂移。
	 */
	@Schema(description = "类目角标：0.无 1.推荐 2.热卖")
	private String badgeType;

	@Schema(description = "租户id")
	private String tenantId;

}
