package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量专区操作执行结果。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "批量专区操作执行结果")
public class ZoneTagBatchResultVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "批次ID（可用于撤销）")
	private String batchId;

	@Schema(description = "总体汇总")
	private Summary summary;

	@Schema(description = "分专区汇总")
	private List<Zone> zones;

	@Schema(description = "系统活动同步结果")
	private Sync sync;

	@Schema(description = "逐商品结果")
	private List<ZoneTagBatchItemVO> items;

	@Data
	@Schema(description = "批量操作汇总")
	public static class Summary implements Serializable {

		private static final long serialVersionUID = 1L;

		private Integer requested;
		private Integer success;
		private Integer changed;
		private Integer added;
		private Integer updated;
		private Integer removed;
		private Integer noChange;
		private Integer failed;

	}

	@Data
	@Schema(description = "批量操作分区汇总")
	public static class Zone implements Serializable {

		private static final long serialVersionUID = 1L;

		private String zone;
		private Integer added;
		private Integer updated;
		private Integer removed;
		private Integer noChange;
		private Integer failed;

	}

	@Data
	@Schema(description = "系统活动同步结果")
	public static class Sync implements Serializable {

		private static final long serialVersionUID = 1L;

		@Schema(description = "SUCCESS / FAILED / SKIPPED")
		private String status;

		private String message;

		private LocalDateTime finishedAt;

	}

}
