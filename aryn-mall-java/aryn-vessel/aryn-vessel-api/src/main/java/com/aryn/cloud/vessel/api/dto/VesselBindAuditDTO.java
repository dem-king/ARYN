package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 船舶绑定申请审核 DTO（管理端）。
 *
 * <p>通过时二选一：
 * <ul>
 *   <li>{@link #matchedVesselId} —— 匹配到系统内已有船舶，直接绑定；</li>
 *   <li>{@link #newVesselName} —— 船尚未录入系统，先建船再绑定
 *       （可同时给出 {@link #newVesselImo} / {@link #newVesselType}）。</li>
 * </ul>
 * 两者都不传则用申请单里的船名按「同名精确匹配」尝试，匹配不到直接报错提示运营选择。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "船舶绑定申请审核DTO")
public class VesselBindAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "通过时：匹配到的已有船舶ID")
	private String matchedVesselId;

	@Schema(description = "通过时：新建船舶名称（船未录入系统时使用）")
	private String newVesselName;

	@Schema(description = "通过时：新建船舶的 IMO 编码")
	private String newVesselImo;

	@Schema(description = "通过时：新建船舶类型（1货轮 2油轮 3集装箱 4散货 5其他）")
	private String newVesselType;

	@Schema(description = "通过时：授予的角色，缺省取申请单的 applyRole")
	private String memberRole;

	@Schema(description = "审核意见 / 驳回原因")
	private String auditRemark;

}
