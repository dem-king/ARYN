
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 发货单物流轨迹
 *
 * @author 雨滴kian
 * @since 2025/4/27
 */
@Data
@Schema(description = "发货单物流轨迹")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "order_delivery_logistics")
public class OrderDeliveryLogistics extends Model<OrderDeliveryLogistics> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "发货单ID")
	private String deliveryId;

	@Schema(description = "物流公司编码（冗余）")
	private String logisticsCompanyCode;

	@Schema(description = "物流公司名称（冗余）")
	private String logisticsCompanyName;

	@Schema(description = "物流单号（冗余）")
	private String logisticsNo;

	@Schema(description = "物流状态")
	private String logisticsStatus;

	@Schema(description = "轨迹描述")
	private String logisticsContext;

	@Schema(description = "轨迹发生时间")
	private LocalDateTime logisticsTime;

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

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@Schema(description = "租户ID")
	private String tenantId;

}
