package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量专区操作逐商品结果。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "批量专区操作逐商品结果")
public class ZoneTagBatchItemVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String spuId;

	private String spuName;

	@Schema(description = "是否成功（业务规则失败为 false）")
	private Boolean success;

	@Schema(description = "变更类型：ADD / UPDATE / REMOVE / NO_CHANGE / INVALID / FAILED")
	private String changeType;

	@Schema(description = "分专区变更")
	private List<ZoneTagChangeVO> changes;

	@Schema(description = "结果说明")
	private String message;

	@Schema(description = "错误码：SPU_NOT_FOUND / INVALID_RULE / NO_ORIGINAL_PRICE / EXPIRED_TIME / ZONE_TAG_LIMIT / NO_ACTION / SYNC_FAILED")
	private String errorCode;

	@Schema(description = "告警")
	private List<ZoneTagWarningVO> warnings;

}
