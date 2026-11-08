package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商详口碑气泡（轮播图浮层出参，最窄白名单）。
 *
 * <p>社证文案与商品卡同源同门槛（订单域 T+1 统计 + {@code SocialProofFormatter} 收口），
 * 好评率与评价区同一口径（goods_score≥4 占比，达标才下发）；头像取该 SPU 近期
 * 非匿名评价的头像快照（评价区本就公开展示，不新增暴露面）。所有字段 null 均表示
 * 未达门槛/数据缺失不展示，前端整体兜底为不渲染气泡。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@Schema(description = "商详口碑气泡（轮播图浮层）")
public class GoodsSocialBubbleVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "社证文案（月售N+/回头客N+/回购率N%），与商品卡同源同门槛；null=未达门槛不展示")
	private String socialText;

	@Schema(description = "社证类型（repurchase=回购率/repeat=回头客/buyer=月售），与 socialText 同生共死；null=未下发或未知")
	private String socialKind;

	@Schema(description = "好评率成文（好评率N%）；null=评价数未达门槛不展示")
	private String praiseText;

	@Schema(description = "好评率数值（good/all，一位小数）；null=未达门槛；供前端本地化兜底")
	private BigDecimal praiseRate;

	@Schema(description = "买家头像（近期非匿名评价快照，最多3个）；空=纯文字胶囊")
	private List<String> avatars;

}
