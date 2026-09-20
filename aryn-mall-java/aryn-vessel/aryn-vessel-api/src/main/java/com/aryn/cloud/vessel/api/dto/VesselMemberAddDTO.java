package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 添加船舶成员 DTO（业务员现场拉人 / 其他有权限角色添加成员）。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "添加船舶成员DTO")
public class VesselMemberAddDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "目标商城用户ID（与 phone 二选一，优先使用）")
	private String userId;

	@Schema(description = "目标用户手机号（按手机号检索商城用户）")
	private String phone;

	@Schema(description = "成员角色：2普通船员 3采购确认人 4业务员")
	@NotBlank(message = "成员角色不能为空")
	private String memberRole;

	@Schema(description = "备注（如：张工-轮机长）")
	private String remark;

}
