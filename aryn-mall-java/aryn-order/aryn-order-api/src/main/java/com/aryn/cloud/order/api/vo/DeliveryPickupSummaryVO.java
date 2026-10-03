package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 出车单取货清单汇总行：把一个出车单下所有任务明细按「商品 + 规格」聚合。
 *
 * <p>仓库按汇总行备货，司机按汇总行核对总件数，所以同名同规格必须并成一行；
 * 规格为空时按空串参与聚合，避免同一商品因「无规格」和「空规格」拆成两行。
 *
 * @author aryn
 * @since 2026/10/1
 */
@Data
public class DeliveryPickupSummaryVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "规格信息")
	private String specsInfo;

	@Schema(description = "取货数量（跨任务合计）")
	private Integer quantity;

	@Schema(description = "商品图片快照")
	private String picUrl;

}
