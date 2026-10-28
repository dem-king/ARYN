package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * C 端会员价查询结果（按 SKU 或按 SPU 两种查询入口共用）。
 *
 * <p>与新人价接口（{@link AppNewbiePriceVO}）同族：供商详 / 购物车 / 商品列表二次聚合使用，
 * **可选登录**——未登录时只下发活动配置的会员价（营销引导），{@link #eligible} 为 null；
 * 携带登录态时下发当前用户等级快照与资格判定，页面据此决定主价切会员价还是展示原价 + 引导文案。
 *
 * <p>商品明细只包含「请求集合 ∩ 活动配置的 SKU ∩ 商品库存在的 SKU」：
 * 按 SKU 查询时请求集合为 skuIds；按 SPU 查询时请求集合为 spuIds（活动配置的 SKU 经商品库回填
 * spuId 后过滤），此时<b>同一 SPU 可能有多条 item</b>（不同规格各自配了会员价），
 * 服务端不做挑选，由 C 端按卡片展示价对应的 SKU 精确匹配。
 *
 * <p>会员价是否真的低于当前售价由展示端按 {@code memberPrice < originalPrice} 判定
 * （与下单链「活动价必须低于当前基价才改价」的倒挂回退口径解耦，避免重放场景两次判定不一致）。
 *
 * <p>一个 SKU 同时被会员价与新人价覆盖时，以会员价为准（会员价是身份权益，
 * 引擎侧会跳过已被会员价覆盖的 SKU 的新人价）。
 *
 * <p><b>会员价与会员折扣率是两套体系，展示需取优</b>：{@link #userDiscountRate} 是用户
 * member_benefit 里的会员折扣率权益（如 0.9 = 9 折），与会员价活动的配置价无关；
 * 结算侧按「会员价 vs 原价 × 会员折扣率」取优成交，展示侧必须用同一取优口径计算
 * 展示价 = min(memberPrice, originalPrice × userDiscountRate)，否则横幅文案「本区享 X 折」
 * 会与实际成交价不符。该字段仅供展示取优，实际成交以订单结算为准。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Data
@Schema(description = "C端会员价查询结果（按SKU或按SPU；可选登录，返回当前用户等级、资格与折扣率）")
public class AppMemberPriceVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "当前用户会员等级ID；null=未登录或无等级")
	private String userLevelId;

	@Schema(description = "当前用户会员等级名称；null=未登录或无等级")
	private String userLevelName;

	@Schema(description = "当前用户是否具备本活动会员价资格（sortOrder 达标）；null=未登录（展示端按营销引导处理）")
	private Boolean eligible;

	/**
	 * 当前用户会员折扣率权益（member_benefit benefitType=1）。
	 *
	 * <p>语义：0 &lt; rate ≤ 1，1 = 无折扣权益；null = 未登录或查询失败。
	 * 仅供展示侧取优：展示价 = min(memberPrice, originalPrice × userDiscountRate)，
	 * 与结算侧「会员价 vs 会员折扣率取优」保持同一口径；实际成交以订单结算为准。
	 */
	@Schema(description = "当前用户会员折扣率权益（0<rate≤1；1=无折扣权益）；null=未登录或查询失败。"
			+ "仅供展示侧取优：展示价=min(memberPrice, originalPrice×userDiscountRate)，实际成交以订单结算为准")
	private BigDecimal userDiscountRate;

	@Schema(description = "命中的会员价商品（仅包含请求且被活动覆盖的 SKU；按 SPU 查询时同一 SPU 可能有多条）")
	private List<MemberSkuPriceVO> items;

	@Data
	@Schema(description = "会员价商品项（按 SKU 一条；同一 SPU 下不同规格各一条，由前端按展示 SKU 匹配）")
	public static class MemberSkuPriceVO implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "SPU ID")
		private String spuId;

		@Schema(description = "会员价（活动配置价，未做倒挂判定）")
		private BigDecimal memberPrice;

		@Schema(description = "划线原价（商品库当前售价）")
		private BigDecimal originalPrice;

		@Schema(description = "活动配置的会员等级门槛ID（引导文案用）")
		private String minLevelId;

		@Schema(description = "活动配置的会员等级名称（引导文案用）")
		private String minLevelName;

	}

}
