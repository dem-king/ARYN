
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 候选订单的商品明细摘要行
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "候选订单商品摘要")
public class DeliveryCandidateItemVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "规格")
	private String specsInfo;

	@Schema(description = "数量")
	private Integer quantity;

	@Schema(description = "商品图片")
	private String picUrl;

}
