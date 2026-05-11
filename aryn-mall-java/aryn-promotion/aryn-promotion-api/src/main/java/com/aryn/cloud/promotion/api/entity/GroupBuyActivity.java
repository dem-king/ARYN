package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "拼团活动")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "group_buy_activity")
public class GroupBuyActivity extends Model<GroupBuyActivity> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动名称")
	@NotBlank(message = "活动名称不能为空")
	private String activityName;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	@Schema(description = "商品原价")
	@NotNull(message = "商品原价不能为空")
	private BigDecimal originalPrice;

	@Schema(description = "拼团价")
	@NotNull(message = "拼团价不能为空")
	private BigDecimal groupPrice;

	@Schema(description = "成团人数")
	@NotNull(message = "成团人数不能为空")
	private Integer groupNum;

	@Schema(description = "限购数量(0=不限)")
	private Integer limitNum;

	@Schema(description = "虚拟成团人数")
	private Integer virtualNum;

	@Schema(description = "活动状态:0草稿,1进行中,2已结束")
	@NotBlank(message = "活动状态不能为空")
	private String activityStatus;

	@Schema(description = "活动开始时间")
	@NotNull(message = "活动开始时间不能为空")
	private LocalDateTime startedAt;

	@Schema(description = "活动结束时间")
	@NotNull(message = "活动结束时间不能为空")
	private LocalDateTime endedAt;

	@Schema(description = "拼团过期时间(小时)")
	private Integer groupExpireHours;

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
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "版本号")
	@Version
	private Integer version;

	@Schema(description = "租户ID")
	private String tenantId;
}
