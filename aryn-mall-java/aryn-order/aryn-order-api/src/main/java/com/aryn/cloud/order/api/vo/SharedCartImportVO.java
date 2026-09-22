package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 补给单 Excel 导入报告（任务 + 行明细）。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单Excel导入报告")
public class SharedCartImportVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "导入任务ID")
	private String importId;

	@Schema(description = "共享购物车ID")
	private String cartId;

	@Schema(description = "上传文件名")
	private String fileName;

	@Schema(description = "状态：1待确认 2已并入 3已取消")
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

	@Schema(description = "确认后实际并入的明细项数")
	private Integer importedRows;

	@Schema(description = "确认时跳过的行数")
	private Integer skippedRows;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "并入补给单时间")
	private LocalDateTime confirmedTime;

	@Schema(description = "报告行")
	private List<SharedCartImportRowVO> rows;

}
