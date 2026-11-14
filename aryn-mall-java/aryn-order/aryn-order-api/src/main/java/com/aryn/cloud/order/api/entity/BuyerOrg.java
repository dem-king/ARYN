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
 * 采购主体（工厂/企业/园区餐厅）。
 *
 * <p>首单权益按 {@code buyer_org} 算一次，不按餐厅/采购员账号重复算。
 * 新建主体先落 {@code audit_status=0}（待审核），待审核允许下企业单但不享首单；
 * 运营审核通过后 {@code audit_status=1} 才具备首单资格（P1 引擎接）。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("buyer_org")
public class BuyerOrg extends Model<BuyerOrg> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "主体编码，租户内唯一")
	private String orgCode;

	@Schema(description = "工厂/企业/餐厅名称")
	private String orgName;

	@Schema(description = "主体类型：1企业 2工厂 3园区餐厅")
	private String orgType;

	@Schema(description = "统一社会信用代码；空值存 NULL")
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

	@Schema(description = "审核状态：0待审核 1已通过 2已驳回 3已停用")
	private String auditStatus;

	@Schema(description = "审核意见")
	private String auditRemark;

	@Schema(description = "审核人")
	private String auditBy;

	@Schema(description = "审核时间")
	private LocalDateTime auditTime;

	@Schema(description = "厂长/管理员商城用户ID")
	private String ownerUserId;

	@Schema(description = "状态：1正常 0停用")
	private String status;

	@Schema(description = "绑定来源：APPLY_AUDIT/INVITE_CODE/MEMBER_ADD/ADMIN_ADD/SHARE_CART/MANUAL")
	private String bindSource;

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
