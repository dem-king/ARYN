package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售员登记/编辑请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售员登记/编辑请求")
public class SalesStaffSaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键（编辑时必填）")
	private String id;

	@Schema(description = "后台员工账号ID")
	@NotBlank(message = "请选择员工账号")
	private String sysUserId;

	@Schema(description = "销售本人的小程序会员ID")
	@NotBlank(message = "请选择销售的小程序会员账号")
	private String memberUserId;

	@Schema(description = "个人提成比例（0-1），为空取全局默认比例")
	private BigDecimal commissionRate;

	@Schema(description = "备注")
	private String remark;

}
