package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 清单直下单确认回执。
 *
 * <p>确认是「解释性」操作：除了可下单的合并清单，还要如实回传任务状态、
 * 跳过的行与原因。只返回 items 会让「全部未处置 / 库存变化跳过」看起来像
 * 成功，前端在空清单或漏行时无法提示用户，静默漏采购。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@Schema(description = "清单直下单确认回执")
public class ReplenishDirectImportConfirmVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "导入任务ID")
	private String importId;

	@Schema(description = "任务状态：1待确认（本次没有可下单商品，需继续处置） 2已确认")
	private String status;

	@Schema(description = "是否可进入结算：仅已确认且清单非空时为 true")
	private boolean checkoutReady;

	@Schema(description = "已复核的 SKU 清单（同 SKU 已合并）")
	private List<ReplenishDirectImportItemVO> items;

	@Schema(description = "确认后的解析行（含跳过原因，供报告页同步展示）")
	private List<ReplenishDirectImportRowVO> rows;

	@Schema(description = "复核通过的明细项数（同 SKU 合并后）")
	private Integer confirmedRows;

	@Schema(description = "本次跳过或未处置的行数（不含未填数量行）")
	private Integer skippedRows;

	@Schema(description = "确认结果说明（无可下单商品 / 部分行跳过时给用户看）")
	private String message;

}
