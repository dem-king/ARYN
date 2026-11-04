package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 按筛选条件取商品 ID 结果。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "按筛选条件取商品 ID 结果")
public class ZoneGoodsIdsVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "筛选结果总数")
	private Long total;

	@Schema(description = "商品ID列表（固定 id 升序）")
	private List<String> ids;

	@Schema(description = "是否因上限被截断")
	private Boolean truncated;

}
