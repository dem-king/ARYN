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
import java.util.List;

@Data
@Schema(description = "拼团记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "group_buy_record")
public class GroupBuyRecord extends Model<GroupBuyRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "拼团活动ID")
	@NotNull(message = "拼团活动ID不能为空")
	private String activityId;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	@Schema(description = "拼团价")
	@NotNull(message = "拼团价不能为空")
	private BigDecimal groupPrice;

	@Schema(description = "成团人数")
	@NotNull(message = "成团人数不能为空")
	private Integer groupNum;

	@Schema(description = "当前参团人数")
	private Integer currentNum;

	@Schema(description = "团长用户ID")
	@NotBlank(message = "团长不能为空")
	private String leaderUserId;

	@Schema(description = "拼团状态:0拼团中,1成功,2失败")
	private String groupStatus;

	@Schema(description = "拼团过期时间")
	@NotNull(message = "过期时间不能为空")
	private LocalDateTime expireAt;

	@Schema(description = "成团成功时间")
	private LocalDateTime successAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	@Version
	private Integer version;

	private String tenantId;

	@TableField(exist = false)
	private List<GroupBuyMember> memberList;

	@TableField(exist = false)
	private GroupBuyActivity activity;
}
