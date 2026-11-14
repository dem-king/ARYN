package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端采购主体视图。
 *
 * <p>信用代码与联系电话按端脱敏：C 端只用于识别「是不是这家工厂」，
 * 不返回完整证照信息；管理端直接返回实体（权限内可见全量）。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体VO")
public class BuyerOrgVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主体ID")
	private String id;

	@Schema(description = "主体编码")
	private String orgCode;

	@Schema(description = "主体名称")
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

	@Schema(description = "审核状态：0待审核 1已通过 2已驳回 3已停用")
	private String auditStatus;

	@Schema(description = "审核意见")
	private String auditRemark;

	@Schema(description = "厂长/管理员用户ID")
	private String ownerUserId;

	@Schema(description = "状态：1正常 0停用")
	private String status;

	@Schema(description = "绑定来源")
	private String bindSource;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "成员数")
	private Integer memberCount;

	@Schema(description = "查看者是否为成员")
	private Boolean joined;

	@Schema(description = "查看者角色：1厂长/管理员 2采购员 3业务员 4查看者")
	private String viewerRole;

	@Schema(description = "查看者是否可加购/建车")
	private Boolean viewerCanEdit;

	@Schema(description = "查看者是否可确认提交")
	private Boolean viewerCanConfirm;

	@Schema(description = "查看者是否为厂长/管理员")
	private Boolean viewerIsOwner;

	@Schema(description = "我的待审核申请状态（无申请为空）")
	private String myApplyStatus;

}
