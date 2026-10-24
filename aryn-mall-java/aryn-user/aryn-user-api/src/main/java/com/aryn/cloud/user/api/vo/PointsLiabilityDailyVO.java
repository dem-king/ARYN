package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 积分负债日报：按日拆解发放 / 抵现 / 兑券 / 过期 / 返还（财务递延负债口径的原始流水聚合）。
 *
 * <p>金额列由服务端按抵现汇率折算（面值口径）；递延负债按「发放确认、消耗结转、过期核销」
 * 三段对账，报表同时给出全量累计与期末余额（见 {@link PointsLiabilityReportVO}）。
 */
@Data
@Schema(description = "积分负债日报")
public class PointsLiabilityDailyVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "统计日期")
	private LocalDate statDate;

	@Schema(description = "新发放积分（获取类流水，不含退款返还）")
	private Integer grantPoints;

	@Schema(description = "退款/取消返还积分（恢复可用，不重复确认负债）")
	private Integer refundReturnPoints;

	@Schema(description = "抵现消耗积分")
	private Integer deductPoints;

	@Schema(description = "兑换消耗积分")
	private Integer exchangePoints;

	@Schema(description = "过期核销积分")
	private Integer expirePoints;

	@Schema(description = "后台调整扣减积分")
	private Integer adminDeductPoints;

	@Schema(description = "新发放折算金额（元，面值口径）")
	private BigDecimal grantAmount;

	@Schema(description = "抵现消耗折算金额（元）——结转营销费用")
	private BigDecimal deductAmount;

	@Schema(description = "兑换消耗折算金额（元）——结转营销费用")
	private BigDecimal exchangeAmount;

	@Schema(description = "过期核销折算金额（元）——负债转销")
	private BigDecimal expireAmount;

}
