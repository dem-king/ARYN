package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 首单核销报表。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@Schema(description = "首单核销报表")
public class FirstOrderReportVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "统计开始时间")
	private String beginTime;

	@Schema(description = "统计结束时间")
	private String endTime;

	@Schema(description = "锁定中笔数")
	private Long lockedCount;

	@Schema(description = "已核销笔数")
	private Long confirmedCount;

	@Schema(description = "已释放笔数")
	private Long releasedCount;

	@Schema(description = "已核销让利总额")
	private BigDecimal discountAmount;

	@Schema(description = "明细行")
	private List<FirstOrderReportRowVO> rows;

}
