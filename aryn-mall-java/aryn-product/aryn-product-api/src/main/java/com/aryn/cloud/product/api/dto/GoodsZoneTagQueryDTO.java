package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量读取商品专区状态请求。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "批量读取商品专区状态请求")
public class GoodsZoneTagQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
	private List<String> spuIds;

	@Schema(description = "是否返回 SKU 覆盖明细")
	private Boolean withSkus;

}
