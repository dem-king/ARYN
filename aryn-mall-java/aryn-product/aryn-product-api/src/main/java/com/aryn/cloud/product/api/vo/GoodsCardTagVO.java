package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品卡营销标签（SPU 级聚合出参，最窄白名单）。
 *
 * <p>新人价/秒杀价都是 SKU 级配置，但商品卡在装修楼层/推荐流只有 SPU 维度，
 * 服务端按 SPU 聚合（同 SPU 多 SKU 命中取最低可享价）。价格字段 null 均表示
 * 未命中，前端回落原售价；社证文案由订单域 T+1 统计格式化下发（门槛/取整在服务端收口）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "商品卡营销标签（SPU级聚合）")
public class GoodsCardTagVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SPU ID")
	private String spuId;

	@Schema(description = "新人专享价；null=未命中（或当前用户无资格）")
	private BigDecimal newbiePrice;

	@Schema(description = "进行中秒杀价；null=未命中")
	private BigDecimal seckillPrice;

	@Schema(description = "社证文案（月售N+/回头客N+/回购率N%）；null=未达门槛不展示")
	private String socialProofText;

	@Schema(description = "社证类型（repurchase=回购率/repeat=回头客/buyer=月售），与 socialProofText 同生共死；null=未下发或未知，前端兜底图标")
	private String socialProofKind;

	@Schema(description = "价格声明文案（近N天最低价，N 随窗口配置）；null=未命中打标条件不展示")
	private String priceTagText;

}
