package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新人资格判定结果。
 *
 * <p>判定口径为「本租户内从未成功支付过订单」（order_info.pay_status='1'），
 * 与「注册 N 天内」时效共同决定是否可享新人价；未支付即取消的单不消耗资格，
 * 付过款含退款的单永久失去资格（与行业主流口径一致，防退款刷新人价）。
 * 任何远程/数据异常按非新人返回（fail-closed），不阻断下单主流程。
 *
 * @author aryn
 * @since 2026/10/13
 */
@Data
@Schema(description = "新人资格判定结果")
public class NewbieEligibilityVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "是否具备新人资格")
	private Boolean eligible;

	@Schema(description = "未通过原因：NOT_LOGGED_IN/ALREADY_PAID/REGISTER_TIMEOUT/PARAM_INVALID/ERROR")
	private String reason;

}
