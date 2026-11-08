package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 清单直下单导入报告（任务 + 行明细）。
 *
 * <p>字段名与 {@link SharedCartImportVO} 对齐（无 cartId/personName），
 * 前端报告页可按同一套渲染逻辑复用。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@Schema(description = "清单直下单导入报告")
public class ReplenishDirectImportVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "导入任务ID")
	private String importId;

	@Schema(description = "上传文件名")
	private String fileName;

	@Schema(description = "状态：1待确认 2已确认 3已取消")
	private String status;

	@Schema(description = "解析出的数据行数")
	private Integer totalRows;

	@Schema(description = "匹配成功行数")
	private Integer matchedRows;

	@Schema(description = "未匹配行数")
	private Integer unmatchedRows;

	@Schema(description = "规格变更行数")
	private Integer specChangedRows;

	@Schema(description = "超库存行数")
	private Integer overStockRows;

	@Schema(description = "数量异常行数")
	private Integer invalidRows;

	@Schema(description = "已下架商品行数")
	private Integer offShelfRows;

	@Schema(description = "未填数量行数（客户本次不采购，非错误）")
	private Integer notFilledRows;

	@Schema(description = "确认后复核通过的明细项数")
	private Integer confirmedRows;

	@Schema(description = "确认时跳过的行数")
	private Integer skippedRows;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "确认时间")
	private LocalDateTime confirmedTime;

	@Schema(description = "报告行")
	private List<ReplenishDirectImportRowVO> rows;

}
