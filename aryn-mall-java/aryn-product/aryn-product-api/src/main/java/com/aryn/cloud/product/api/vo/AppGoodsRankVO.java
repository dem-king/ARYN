package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端商品榜单条目 VO（热搜榜 / 热销榜共用，最窄白名单出参）。
 *
 * <p>榜单只表达名次，不含价格/库存/销量数字：搜索页「热门榜单」只展示
 * 名次 + 图 + 名称 + 副标题，不展示绝对销量（见 docs 需求文档 2026-10-25）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Data
@Schema(description = "C端商品榜单条目VO")
public class AppGoodsRankVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SPU ID")
	private String id;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "副标题")
	private String subTitle;

	@Schema(description = "商品主图（取商品图集首图）")
	private String picUrl;

}
