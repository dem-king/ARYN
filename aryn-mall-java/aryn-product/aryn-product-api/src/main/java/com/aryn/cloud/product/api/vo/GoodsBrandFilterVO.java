package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C 端品牌筛选项 VO。
 *
 * <p>分类页/搜索页的品牌筛选条不能直接展示全租户品牌：本租户 42 个品牌里，
 * 单个一级分类最多只覆盖 10 个，水果、海鲜水产、船舶物料等分类更是**一个都没有**
 * （商品无 brand_id）。展示全量品牌会让用户点到大量「必然为空」的选项。
 *
 * <p>因此品牌条改为按当前查询条件（分类 / 关键词）聚合出**实际有在售商品**的品牌，
 * 并带上商品数供前端提示，属于分面导航（faceted navigation）的常规做法：
 * 分面值随结果集动态生成、零结果的分面值不展示。
 *
 * @author aryn
 * @since 2026/9/29
 */
@Data
@Schema(description = "C端品牌筛选项VO")
public class GoodsBrandFilterVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "品牌ID")
	private String id;

	@Schema(description = "品牌名称")
	private String name;

	@Schema(description = "品牌 Logo")
	private String logoUrl;

	/**
	 * 该品牌在当前查询条件下的在售商品数。
	 *
	 * <p>聚合语义与 {@code GoodsSpuMapper.selectApiPage} 对齐，因此恒 &ge; 1：
	 * 计数为 0 的品牌不会出现在结果里（这正是本 VO 存在的原因）。
	 */
	@Schema(description = "该品牌在当前条件下的在售商品数")
	private Integer goodsCount;

}
