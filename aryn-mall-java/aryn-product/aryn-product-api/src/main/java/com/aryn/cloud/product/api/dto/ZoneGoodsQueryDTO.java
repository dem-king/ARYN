package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 专区商品管理分页查询条件。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区商品管理分页查询条件")
public class ZoneGoodsQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "当前页")
	private long current = 1;

	@Schema(description = "每页条数")
	private long size = 10;

	@Schema(description = "专区：member / clearance", requiredMode = Schema.RequiredMode.REQUIRED)
	private String zoneType;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "二级类目ID")
	private String categorySecondId;

	@Schema(description = "品牌ID")
	private String brandId;

	@Schema(description = "商品状态：1上架 0下架，空=全部")
	private String status;

}
