package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单个商品在批量操作预检/状态查询中的状态。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "商品专区状态")
public class ZoneTagItemStateVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "商品状态：1上架 0下架")
	private String spuStatus;

	@Schema(description = "当前标签与规则快照")
	private ZoneRuleSnapshotVO before;

	@Schema(description = "拟变更（预检返回）")
	private List<ZoneTagChangeVO> changes;

	@Schema(description = "商品级告警")
	private List<ZoneTagWarningVO> warnings;

	@Schema(description = "SKU 明细（withSkus=true 时返回）")
	private List<ZoneSkuStateVO> skus;

}
