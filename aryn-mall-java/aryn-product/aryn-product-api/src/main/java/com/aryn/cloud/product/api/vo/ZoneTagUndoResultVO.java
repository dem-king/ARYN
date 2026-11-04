package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 撤销批次结果。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "撤销批次结果")
public class ZoneTagUndoResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String batchId;

	@Schema(description = "成功恢复数")
	private Integer restored;

	@Schema(description = "跳过数（当前值已被后续修改）")
	private Integer skipped;

	@Schema(description = "冲突明细")
	private List<Conflict> conflicts;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "撤销冲突项")
	public static class Conflict implements Serializable {

		private static final long serialVersionUID = 1L;

		private String spuId;

		private String spuName;

		private String reason;

	}

}
