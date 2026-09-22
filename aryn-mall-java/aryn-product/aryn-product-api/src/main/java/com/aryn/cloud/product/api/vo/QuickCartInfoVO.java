package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 快捷加购信息 VO。
 *
 * <p>商品列表/推荐位上的「快捷加购」按钮不带完整的 SKU 明细，
 * 点击时按需拉取一次本 VO：单规格商品直接给出可加购的 SKU，
 * 多规格商品返回 {@code choose} 交由用户选择规格，避免列表接口背负全部 SKU。
 *
 * <p>数量规则（MOQ/步长）来自 {@code ship_sku_profile}：前端据此给出
 * 可直接提交的最小起订数量，避免加购成功后卡在结算环节。
 *
 * @author aryn
 * @since 2026/9/21
 */
@Data
@Schema(description = "快捷加购信息VO")
public class QuickCartInfoVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 可直接加购，无需选择规格 */
	public static final String MODE_DIRECT = "direct";

	/** 需用户选择规格（多规格商品） */
	public static final String MODE_CHOOSE = "choose";

	/** 当前不可加购（售罄/无有效 SKU） */
	public static final String MODE_UNAVAILABLE = "unavailable";

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "商品主图")
	private String[] spuUrls;

	/**
	 * 商品全部在售 SKU（仅 mode=choose 时返回）。
	 *
	 * <p>多规格商品必须由用户选择规格，列表接口不携带 SKU，
	 * 因此在这里一次性补齐，避免前端再取一次商品详情（那会写入浏览足迹）。
	 */
	@Schema(description = "SKU 列表（mode=choose 时返回）")
	private List<QuickCartSkuVO> goodsSkus;

	@Schema(description = "多规格：0.否；1.是")
	private String enableSpecs;

	@Schema(description = "加购方式：direct 直接加购；choose 需选择规格；unavailable 不可加购")
	private String mode;

	@Schema(description = "SKU ID（mode=direct 时返回）")
	private String skuId;

	@Schema(description = "SKU 显示图")
	private String picUrl;

	@Schema(description = "规格描述")
	private String specsInfo;

	@Schema(description = "售价（元）")
	private BigDecimal salesPrice;

	@Schema(description = "可售库存")
	private Integer stock;

	@Schema(description = "最小起订量")
	private Integer moq;

	@Schema(description = "数量步长")
	private Integer stepQty;

	@Schema(description = "采购单位")
	private String purchaseUnit;

	@Schema(description = "不可加购原因（mode=unavailable 时返回）")
	private String reason;

}
