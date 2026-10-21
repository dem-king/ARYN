package com.aryn.cloud.vessel.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 绑定申请审核预检结果。
 *
 * <p>业务背景：审核员（运营）通常不知道申请里的船是否已在系统内，
 * 原设计只能靠「按船名精确匹配」在提交后失败才发现。预检把这一步提前到
 * <b>打开审核弹窗时</b>：按申请单填写的 IMO/呼号与船名各查一次在营船舶，
 * 直接给出一句话建议与候选列表，审核员只需确认或推翻。
 *
 * @author aryn
 * @since 2026/10/16
 */
@Data
@Schema(description = "绑定申请审核预检VO")
public class VesselBindPrecheckVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "申请ID")
	private String applyId;

	@Schema(description = "申请船名")
	private String applyVesselName;

	@Schema(description = "申请单填写的 IMO 编号")
	private String applyVesselImo;

	@Schema(description = "申请单填写的呼号")
	private String applyVesselCallSign;

	/**
	 * 建议落点：existing=绑定已有船舶；new=新建船舶后绑定。
	 * 前端据此预选审核方式，审核员仍可手动改。
	 */
	@Schema(description = "建议落点：existing 绑定已有 / new 新建后绑定")
	private String suggestedMode;

	@Schema(description = "建议预选的船舶ID（候选唯一时给出，多条时留空由审核员选）")
	private String suggestedVesselId;

	@Schema(description = "给审核员看的一句话结论")
	private String suggestion;

	@Schema(description = "命中的候选船舶：IMO/呼号命中优先，其次同名，最后相近船名")
	private List<VesselBindVesselOptionVO> candidates;

}
