package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批量专区操作批次头（操作记录列表）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "批量专区操作批次")
public class ZoneTagBatchVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String batchId;

	private String batchNo;

	private String operatorId;

	private String operatorName;

	private LocalDateTime createTime;

	private Integer total;

	private Integer changed;

	private Integer added;

	private Integer updated;

	private Integer removed;

	private Integer noChange;

	private Integer failed;

	@Schema(description = "会员专区动作：ADD/REMOVE/NONE")
	private String memberAction;

	@Schema(description = "低价捡漏动作：ADD/REMOVE/NONE")
	private String clearanceAction;

	@Schema(description = "同步状态：SUCCESS/FAILED/SKIPPED")
	private String syncStatus;

	private String syncMessage;

	@Schema(description = "撤销状态：NONE/UNDONE")
	private String undoStatus;

	private LocalDateTime undoTime;

}
