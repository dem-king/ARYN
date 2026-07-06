
package com.aryn.cloud.product.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 商品品牌
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@Schema(description = "商品品牌")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "pms_brand")
public class Brand extends Model<Brand> {

	@Schema(description = "品牌ID")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "品牌名称")
	private String name;

	@Schema(description = "品牌Logo")
	private String logo;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "首字母")
	private String firstLetter;

	@Schema(description = "状态(0禁用1启用)")
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
	@Schema(description = "逻辑删除(0正常1删除)")
	private String delFlag;

}