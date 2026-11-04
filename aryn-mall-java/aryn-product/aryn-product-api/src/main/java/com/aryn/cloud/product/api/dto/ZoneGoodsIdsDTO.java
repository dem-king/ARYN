package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 按筛选条件取商品 ID 请求（全选筛选结果 / 换一批）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "按筛选条件取商品 ID 请求")
public class ZoneGoodsIdsDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "专区：member / clearance / none / not_member / not_clearance")
	private String zoneType;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "二级类目ID")
	private String categorySecondId;

	@Schema(description = "品牌ID")
	private String brandId;

	@Schema(description = "商品状态：1上架 0下架")
	private String status;

	@Schema(description = "返回上限：默认 500，最大 1000")
	private Integer limit;

}
