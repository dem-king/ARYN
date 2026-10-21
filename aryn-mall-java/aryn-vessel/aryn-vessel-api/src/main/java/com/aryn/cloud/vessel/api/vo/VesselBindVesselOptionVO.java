package com.aryn.cloud.vessel.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审核弹窗的候选船舶（仅含认船需要的字段，不下发整份船舶档案）。
 *
 * @author aryn
 * @since 2026/10/16
 */
@Data
@Schema(description = "绑定申请审核候选船舶VO")
public class VesselBindVesselOptionVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID")
	private String id;

	@Schema(description = "船舶中文名称")
	private String vesselName;

	@Schema(description = "IMO 编号")
	private String imoCode;

	@Schema(description = "呼号")
	private String callSign;

	@Schema(description = "命中原因：IMO一致 / 呼号一致 / 船名相同 / 船名相近；普通检索为空")
	private String matchReason;

}
