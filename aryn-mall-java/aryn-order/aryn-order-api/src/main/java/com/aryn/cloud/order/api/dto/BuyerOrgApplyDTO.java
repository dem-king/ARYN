package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端采购主体申请 DTO：创建新主体（applyType=1）或申请加入已有主体（applyType=2）。
 *
 * <p>创建申请会同步落一条待审核主体与厂长成员行，审核通过后自动转为可用；
 * 加入申请只落申请单，厂长或运营审核通过后才成为成员。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体申请DTO")
public class BuyerOrgApplyDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "申请类型不能为空")
	@Schema(description = "申请类型：1创建主体 2加入已有主体")
	private String applyType;

	@Schema(description = "加入时填已有主体ID（applyType=2 必填）")
	private String buyerOrgId;

	@Schema(description = "申请主体名称（applyType=1 必填）")
	private String orgName;

	@Schema(description = "主体类型：1企业 2工厂 3园区餐厅")
	private String orgType;

	@Schema(description = "统一社会信用代码")
	private String creditCode;

	@Schema(description = "营业执照/门头照URL")
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

	@Schema(description = "申请角色：默认 2采购员；创建主体固定为 1厂长/管理员")
	private String applyRole;

	@Schema(description = "备注")
	private String remark;

}
