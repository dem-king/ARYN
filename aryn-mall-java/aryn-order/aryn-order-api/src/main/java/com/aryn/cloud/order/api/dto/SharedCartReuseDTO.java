package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 历史补给单一键复用 DTO。
 *
 * <p>复用语义：把一张**历史**共享购物车的明细搬进「当前靠港计划」下的新车，
 * 作为本次采购的起点（用户随后仍可增删改）。这不是下单，因此不在此处校验
 * 库存/价格/数量规则 —— 那些在提交整船订单时统一兜底。
 *
 * <p>为什么要求传 vesselId：补给清单是**按船**组织的（船员、航线、靠港不同），
 * 把 A 船的清单搬到 B 船没有业务意义，因此服务端会校验 vesselId 与源单一致。
 * vesselCallId 取当前靠港计划，复用出来的新单绑定它、而不是源单那个已完成的历史靠港。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "历史补给单复用DTO")
public class SharedCartReuseDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID（必须与源单一致）")
	@NotBlank(message = "船舶ID不能为空")
	private String vesselId;

	@Schema(description = "本次采购的靠港计划ID")
	@NotBlank(message = "靠港计划ID不能为空")
	private String vesselCallId;

	@Schema(description = "备注（覆盖源单备注；不传则沿用源单备注）")
	private String remark;

}
