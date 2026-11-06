
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
	 * 类目角标：0.无；其它值为「类目角标」配置的 goods_category_badge.badge_key
	 *
	 * <p>分类页左栏在类目名前渲染一个小方块（「荐」「热」等），文案与配色由运营在
	 * 管理后台「商品管理 → 类目角标」配置，类目只引用配置的稳定键，新增角标不需要改代码发版。
	 * 引用不到启用配置时（未配置/已停用/已删除/非法 key）按「无角标」处理。
	 */
	@Schema(description = "类目角标：0.无；其它值为角标配置的 badgeKey")
	private String badgeType;

	@Schema(description = "租户id")
	private String tenantId;

}
