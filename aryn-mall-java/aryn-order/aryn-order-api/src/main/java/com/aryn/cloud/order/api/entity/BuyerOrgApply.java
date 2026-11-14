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
 * 采购主体创建/加入申请。
 *
 * <p>创建申请提交时同步落一条待审核 {@code buyer_org}（owner 已是成员），
 * 因此待审核主体可以立刻建岸上车下单，只是不享首单；
 * 审核动作只更新主体审核态与申请单状态，不重复建主体。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体创建/加入申请")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("buyer_org_apply")
public class BuyerOrgApply extends Model<BuyerOrgApply> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "申请单号")
	private String applyNo;

	@Schema(description = "申请人商城用户ID")
	private String userId;

	@Schema(description = "申请类型：1创建主体 2加入已有主体")
	private String applyType;

	@Schema(description = "加入时填已有主体ID")
	private String buyerOrgId;

	@Schema(description = "申请主体名称")
	private String orgName;

	@Schema(description = "主体类型：1企业 2工厂 3园区餐厅")
	private String orgType;

	@Schema(description = "统一社会信用代码")
	private String creditCode;

	@Schema(description = "营业执照/门头照")
	private String licenseUrl;

	@Schema(description = "省")
	private String province;

	@Schema(description = "市")
	private String city;

	@Schema(description = "区/县")
	private String district;

	@Schema(description = "详细地址")
	private String address;

	@Schema(description = "联系人")
	private String contactName;

	@Schema(description = "联系电话")
	private String contactPhone;

	@Schema(description = "申请角色：1厂长/管理员 2采购员 3业务员 4查看者")
	private String applyRole;

	@Schema(description = "状态：1待审核 2通过 3驳回 4取消")
	private String status;

	@Schema(description = "审核人")
	private String auditBy;

	@Schema(description = "审核时间")
	private LocalDateTime auditTime;

	@Schema(description = "审核意见")
	private String auditRemark;

	@Schema(description = "审核通过后落的主体ID")
	private String matchedOrgId;

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
