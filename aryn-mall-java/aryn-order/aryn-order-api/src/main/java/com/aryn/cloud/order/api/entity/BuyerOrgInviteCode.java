package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 采购主体邀请码。
 *
 * <p>邀请码/分享卡加入免审，与「搜索申请需厂长或运营审」区分开。
 * 一个主体同一时刻只保留一个有效码（生成新码时旧码置为失效），便于管理。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体邀请码")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("buyer_org_invite_code")
public class BuyerOrgInviteCode extends Model<BuyerOrgInviteCode> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "采购主体ID")
	private String buyerOrgId;

	@Schema(description = "邀请码，租户内唯一")
	private String code;

	@Schema(description = "过期时间")
	private LocalDateTime expiresAt;

	@Schema(description = "最大使用次数，0 表示不限")
	private Integer maxUses;

	@Schema(description = "已使用次数")
	private Integer usedCount;

	@Schema(description = "状态：1有效 0已失效")
	private String status;

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
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}
