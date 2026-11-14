package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端采购主体保存 DTO（运营代建/修改资料）。
 *
 * <p>信用代码租户内唯一；为空按 NULL 落库，不参与唯一冲突。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体保存DTO")
public class BuyerOrgSaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "主体名称不能为空")
	@Schema(description = "工厂/企业/餐厅名称")
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

	@Schema(description = "厂长/管理员商城用户ID；填写后自动建成员行")
	private String ownerUserId;

	@Schema(description = "备注")
	private String remark;

}
