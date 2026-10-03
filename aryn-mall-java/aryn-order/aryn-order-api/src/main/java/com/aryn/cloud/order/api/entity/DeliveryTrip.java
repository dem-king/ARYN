
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.order.api.vo.DeliveryPickupSummaryVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出车单
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "出车单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_trip")
public class DeliveryTrip extends Model<DeliveryTrip> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "出车单号（系统生成）")
	private String tripNo;

	@Schema(description = "配送员ID")
	private String staffId;

	@Schema(description = "状态：1待配货 2配货中 3配送中 4已完成")
	private String status;

	@Schema(description = "关联订单数")
	private Integer taskCount;

	@Schema(description = "仓库地址快照")
	private String warehouseAddress;

	@Schema(description = "开始配货时间")
	private LocalDateTime startLoadTime;

	@Schema(description = "装货出发时间")
	private LocalDateTime departTime;

	@Schema(description = "全部完成时间")
	private LocalDateTime completeTime;

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

	@Schema(description = "配送员姓名（按 staffId 关联查询的派生值）")
	@TableField(exist = false)
	private String staffName;

	@Schema(description = "关联配送任务列表")
	@TableField(exist = false)
	private List<DeliveryTask> taskList;

	@Schema(description = "取货清单汇总（按商品聚合的派生值，跨任务合并同规格）")
	@TableField(exist = false)
	private List<DeliveryPickupSummaryVO> pickupSummary;

	@Schema(description = "总件数（按任务明细汇总的派生值）")
	@TableField(exist = false)
	private Integer totalItemCount;

	@Schema(description = "已取件数（派生值）")
	@TableField(exist = false)
	private Integer pickedItemCount;

	@Schema(description = "已送达单数（按任务送达时间统计的派生值）")
	@TableField(exist = false)
	private Integer arrivedTaskCount;

	@Schema(description = "仓库名称（取自仓库配置的派生值）")
	@TableField(exist = false)
	private String warehouseName;

	@Schema(description = "搜索关键字")
	@TableField(exist = false)
	private String keyword;

}