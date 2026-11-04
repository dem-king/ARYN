package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 批量专区操作批次详情（头 + 明细）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "批量专区操作批次详情")
public class ZoneTagBatchDetailVO extends ZoneTagBatchVO {

	private static final long serialVersionUID = 1L;

	@Schema(description = "逐商品明细")
	private List<ZoneTagBatchItemVO> items;

}
