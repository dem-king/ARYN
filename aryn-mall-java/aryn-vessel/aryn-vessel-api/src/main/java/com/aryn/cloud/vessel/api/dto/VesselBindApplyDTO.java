package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 船舶绑定 / 认领申请 DTO。
 *
 * <p>两类申请共用：业务员认领船舶（applyRole=4）、船员申请上船（applyRole=2）。
 * 船名是自由文本而非下拉选择——因为**船可能还没录入系统**，
 * 由运营在审核时匹配已有船舶或新建船舶。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "船舶绑定申请DTO")
public class VesselBindApplyDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "申请角色：2普通船员 4业务员（销售认领船舶）")
	private String applyRole;

	@Schema(description = "船名（自由文本，允许系统内尚未录入的船）")
	@NotBlank(message = "船名不能为空")
	private String applyVesselName;

	@Schema(description = "IMO 或呼号（选填，有助于运营匹配）")
	private String applyVesselImo;

	@Schema(description = "常靠港口（选填）")
	private String applyPortName;

	@Schema(description = "真实姓名")
	private String realName;

	@Schema(description = "联系电话")
	private String phone;

	@Schema(description = "船上职务 / 业务员工号")
	private String position;

	@Schema(description = "补充说明")
	private String remark;

}
