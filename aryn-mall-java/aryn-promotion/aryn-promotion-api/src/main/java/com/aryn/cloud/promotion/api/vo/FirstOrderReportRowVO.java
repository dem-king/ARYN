package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 首单核销报表行（活动 × 主体类型）。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@Schema(description = "首单核销报表行")
public class FirstOrderReportRowVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "主体类型：1船 2采购主体")
	private String subjectType;

	@Schema(description = "占用/锁定笔数")
	private Long lockedCount;

	@Schema(description = "已核销笔数（已支付确认）")
	private Long confirmedCount;

	@Schema(description = "已释放笔数（取消/超时）")
	private Long releasedCount;

	@Schema(description = "去重主体数")
	private Long subjectCount;

	@Schema(description = "已核销让利金额")
	private BigDecimal discountAmount;

	@Schema(description = "活动月预算；0 表示未配置")
	private BigDecimal monthlyBudget;

	@Schema(description = "本月已占用（锁定+确认）")
	private BigDecimal monthlyUsed;

	@Schema(description = "月预算使用率（0-100）；未配置预算为 null")
	private BigDecimal usageRate;

	@Schema(description = "是否达到 80% 预警线")
	private Boolean alert;

}
