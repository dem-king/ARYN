package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采购主体申请单视图。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体申请VO")
public class BuyerOrgApplyVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "申请单ID")
	private String id;

	@Schema(description = "申请单号")
	private String applyNo;

	@Schema(description = "申请人用户ID")
	private String userId;

	@Schema(description = "申请人昵称")
	private String userName;

	@Schema(description = "申请人脱敏手机号")
	private String userPhoneMasked;

	@Schema(description = "申请类型：1创建主体 2加入已有主体")
	private String applyType;

	@Schema(description = "加入时填已有主体ID")
	private String buyerOrgId;

	@Schema(description = "申请主体名称")
	private String orgName;

	@Schema(description = "主体类型：1企业 2工厂 3园区餐厅")
	private String orgType;

	@Schema(description = "脱敏信用代码")
	private String creditCodeMasked;

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

	@Schema(description = "脱敏联系电话")
	private String contactPhoneMasked;

	@Schema(description = "申请角色")
	private String applyRole;

	@Schema(description = "状态：1待审核 2通过 3驳回 4取消")
	private String status;

	@Schema(description = "审核意见")
	private String auditRemark;

	@Schema(description = "审核通过后落的主体ID")
	private String matchedOrgId;

	@Schema(description = "申请时间")
	private LocalDateTime createTime;

}
