
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 配送范围
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "配送范围")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_area")
public class DeliveryArea extends Model<DeliveryArea> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "省编码")
	private String provinceCode;

	@Schema(description = "省名称")
	private String provinceName;

	@Schema(description = "市编码")
	private String cityCode;

	@Schema(description = "市名称")
	private String cityName;

	@Schema(description = "区县编码")
	private String areaCode;

	@Schema(description = "区县名称")
	private String areaName;

	@Schema(description = "启用状态：1启用 0禁用")
	private String enabled;

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