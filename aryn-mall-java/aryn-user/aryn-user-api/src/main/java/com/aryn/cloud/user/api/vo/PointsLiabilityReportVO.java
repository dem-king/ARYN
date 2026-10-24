package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 积分负债报表：区间日报 + 全量累计 + 期末负债。
 *
 * <p>财务口径（递延负债三段对账）：发放时确认负债（面值口径折算）、
 * 抵现/兑券消耗时结转营销费用、过期时核销转销；期末余额 = 用户未消耗积分合计。
 * 预计消耗率（行业常见 85%-95%）不做硬编码折现，报表按面值口径展示，
 * 由财务按自身口径在表外调整。
 */
@Data
@Schema(description = "积分负债报表")
public class PointsLiabilityReportVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "抵现汇率（每元所需积分），金额折算依据；0=未配置不折算")
	private Integer pointsPerYuan;

	@Schema(description = "区间日报（按日期倒序）")
	private List<PointsLiabilityDailyVO> days = new ArrayList<>();

	@Schema(description = "期末未消耗积分余额（全体用户 point 合计）")
	private Long balancePoints;

	@Schema(description = "期末负债折算金额（元，面值口径）")
	private BigDecimal balanceAmount;

	@Schema(description = "全量累计发放积分（获取类，不含退款返还）")
	private Long totalGrantPoints;

	@Schema(description = "全量累计抵现积分（结转营销费用）")
	private Long totalDeductPoints;

	@Schema(description = "全量累计兑换积分（结转营销费用）")
	private Long totalExchangePoints;

	@Schema(description = "全量累计过期核销积分（负债转销）")
	private Long totalExpirePoints;

	@Schema(description = "全量累计退款返还积分（恢复可用，不重复确认负债）")
	private Long totalRefundReturnPoints;

	@Schema(description = "区间内发放合计（日报汇总）")
	private Long rangeGrantPoints;

	@Schema(description = "区间内消耗合计（抵现+兑换+后台扣减，日报汇总）")
	private Long rangeConsumePoints;

	@Schema(description = "区间内过期核销合计（日报汇总）")
	private Long rangeExpirePoints;

	/**
	 * 全量累计单行查询结果（Mapper 映射用，字段与外层同名合计一一对应）。
	 */
	@Data
	@Schema(description = "积分负债全量累计")
	public static class Summary implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		private Long totalGrantPoints;

		private Long totalDeductPoints;

		private Long totalExchangePoints;

		private Long totalExpirePoints;

		private Long totalRefundReturnPoints;

	}

}
