
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配送任务
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "配送任务")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_task")
public class DeliveryTask extends Model<DeliveryTask> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "任务编号")
	private String taskNo;

	@Schema(description = "关联出车单ID，待派单时为空")
	private String tripId;

	@Schema(description = "关联订单ID")
	private String orderId;

	@Schema(description = "订单号冗余")
	private String orderNo;

	@Schema(description = "配送员ID，待派单时为空")
	private String staffId;

	@Schema(description = "状态：1待派单 2待取货 3配货中 4待送达 5已送达 6已签收 7已取消 8异常 9待退回")
	private String status;

	@Schema(description = "送货顺序")
	private Integer sortNo;

	@Schema(description = "取货仓库地址快照")
	private String warehouseAddress;

	@Schema(description = "收货人姓名")
	private String recipientName;

	@Schema(description = "收货人电话")
	private String recipientPhone;

	@Schema(description = "收货完整地址")
	private String recipientAddress;

	@Schema(description = "派单时间")
	private LocalDateTime assignTime;

	@Schema(description = "取货开始时间")
	private LocalDateTime pickUpTime;

	@Schema(description = "出发配送时间")
	private LocalDateTime departTime;

	@Schema(description = "送达时间")
	private LocalDateTime arriveTime;

	@Schema(description = "签收时间")
	private LocalDateTime signTime;

	@Schema(description = "异常时间")
	private LocalDateTime exceptionTime;

	@Schema(description = "待退回时间")
	private LocalDateTime returnPendingTime;

	@Schema(description = "退回确认时间")
	private LocalDateTime returnConfirmTime;

	@Schema(description = "关闭时间")
	private LocalDateTime closeTime;

	@Schema(description = "当前尝试号，从1递增")
	private Integer attemptNo;

	@Schema(description = "乐观锁版本号")
	@Version
	private Integer version;

	@Schema(description = "异常原因编码")
	private String exceptionReason;

	@Schema(description = "异常说明")
	private String exceptionDesc;

	@Schema(description = "备注")
	private String remark;

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

	@Schema(description = "取货明细列表")
	@TableField(exist = false)
	private List<DeliveryTaskItem> itemList;

	@Schema(description = "搜索关键字")
	@TableField(exist = false)
	private String keyword;

}