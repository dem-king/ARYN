package com.aryn.cloud.boot.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 品牌筛选项与商品列表的谓词语义一致性契约。
 *
 * <p>背景（真实缺陷）：C 端品牌条拉的是全租户品牌（42 个），与当前分类无关；
 * 本租户水果分类 11 件商品**全部没有 brand_id**，于是每个品牌点进去都是空列表。
 * 修法是新增按条件聚合的品牌接口 {@code /app/goodsbrand/filter-list}，
 * 只返回「当前条件下确有在售商品」的品牌。
 *
 * <p>该修法成立的前提是**两条 SQL 选中同一批商品**：筛选接口的谓词必须逐条对齐
 * {@code GoodsSpuMapper.selectApiPage}。一旦有人只改一边（例如给列表加了新的
 * 上架条件、或把分类条件从 OR 改成 AND 却只在列表侧落），品牌条上的计数就会与
 * 点进去的条数不符 —— 编译、类型检查、单测全绿，只有真机点分类才暴露。
 * 因此这里把两处谓词绑成一条静态契约。
 */
class BrandFilterPredicateContractTest {

	/** 商品列表（C 端）查询 */
	private static final String SPU_MAPPER = "aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsSpuMapper.xml";

	/** 品牌筛选项聚合查询 */
	private static final String BRAND_MAPPER = "aryn-product/aryn-product-biz/src/main/resources/mapper/GoodsBrandMapper.xml";

	/**
	 * 两边都必须出现的「可见性谓词」：商品/类目的逻辑删除与上架状态。
	 *
	 * <p>断言写成不含空格的紧凑形态，避免 XML 里的换行与缩进差异导致误判；
	 * 比对前会先把 SQL 压平。
	 */
	private static final List<String> SHARED_VISIBILITY_PREDICATES = List.of(
			"goods_spu.`del_flag`='0'",
			"goods_spu.`status`='1'",
			"goods_category.`del_flag`='0'");

	/** 两边都必须支持的可选查询条件 */
	private static final List<String> SHARED_FILTER_CONDITIONS = List.of(
			"#{query.categoryFirstId}",
			"#{query.categorySecondId}",
			"#{query.name}");

	private final Path projectRoot = findProjectRoot();

	@Test
	void filterQueryReusesTheSameCategoryAndKeywordPredicates() throws IOException {
		String listSql = compact(selectBody(Files.readString(projectRoot.resolve(SPU_MAPPER)), "selectApiPage"));
		String facetSql = compact(selectBody(Files.readString(projectRoot.resolve(BRAND_MAPPER)), "selectFilterList"));

		for (String predicate : SHARED_VISIBILITY_PREDICATES) {
			assertThat(facetSql)
					.as("品牌筛选项缺少可见性谓词 %s —— 会导致计数与商品列表条数不一致", predicate)
					.contains(predicate);
			assertThat(listSql)
					.as("商品列表侧可见性谓词已变化（%s），品牌筛选项需同步", predicate)
					.contains(predicate);
		}

		for (String condition : SHARED_FILTER_CONDITIONS) {
			assertThat(facetSql)
					.as("品牌筛选项未支持条件 %s —— 该维度下会出现「点了必然为空」的品牌", condition)
					.contains(condition);
			assertThat(listSql)
					.as("商品列表侧条件已变化（%s），品牌筛选项需同步", condition)
					.contains(condition);
		}
	}

	/**
	 * 关键词匹配必须两边都走 LOWER(...) LIKE，否则大小写口径不一致
	 * （例如搜「Apple」时列表有货、品牌条却没有）。
	 */
	@Test
	void keywordMatchingStaysCaseInsensitiveOnBothSides() throws IOException {
		String listSql = compact(selectBody(Files.readString(projectRoot.resolve(SPU_MAPPER)), "selectApiPage"));
		String facetSql = compact(selectBody(Files.readString(projectRoot.resolve(BRAND_MAPPER)), "selectFilterList"));

		String pattern = compact("LOWER(goods_spu.`name`) LIKE CONCAT('%',LOWER(#{query.name}),'%')");
		assertThat(listSql).contains(pattern);
		assertThat(facetSql).contains(pattern);
	}

	/**
	 * 筛选接口必须用 INNER JOIN 商品表把「零商品的品牌」挡在结果之外 ——
	 * 这正是修复的目标；靠前端过滤会在分页/缓存等场景漏回死选项。
	 */
	@Test
	void filterQueryDropsBrandsWithoutGoods() throws IOException {
		String facetSql = compact(selectBody(Files.readString(projectRoot.resolve(BRAND_MAPPER)), "selectFilterList"));

		assertThat(facetSql).contains(compact(
				"INNER JOIN goods_spu AS goods_spu ON goods_spu.brand_id = goods_brand.id"));
		// 只展示启用品牌，与 /app/goodsbrand/list 的口径一致
		assertThat(facetSql).contains("goods_brand.`del_flag`='0'");
		assertThat(facetSql).contains("goods_brand.`status`='0'");
		assertThat(facetSql).contains(compact("COUNT(goods_spu.id) AS goods_count"));
	}

	/** 取出指定 {@code <select>} 的正文 */
	private static String selectBody(String xml, String id) {
		int start = xml.indexOf("<select id=\"" + id + "\"");
		assertThat(start).as("未找到 select#%s", id).isGreaterThanOrEqualTo(0);
		int end = xml.indexOf("</select>", start);
		assertThat(end).as("select#%s 未闭合", id).isGreaterThan(start);
		return xml.substring(start, end);
	}

	/**
	 * 压平 SQL 便于比对：去掉全部空白。
	 *
	 * <p>XML 里谓词换行、缩进各异（`goods_spu.\n.\nid` 这种形态在 goodsSpuSql 里真实存在），
	 * 逐字符比对前必须先归一化。
	 */
	private static String compact(String sql) {
		return sql.replaceAll("\\s+", "");
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("未找到包含 db/cloud 的项目根目录");
		}
		return current;
	}

}
