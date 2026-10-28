package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 {@code GoodsSpuMapper.xml#selectApiPage} 的 SQL 契约（不连数据库，直接解析 mapper XML 并生成 BoundSql）。
 *
 * <p>F1「低价捡漏」的两个回归重点都在 SQL 层，Mock 测不出来：
 * <ol>
 * <li>不携带 bargainRate 时 SQL 与历史逐字一致（无 bargain 过滤、无 XML ORDER BY，排序仍交给分页插件）；</li>
 * <li>携带 bargainRate 时过滤+固定排序齐全，且阈值只作为绑定值（{@code ?}），不拼进 SQL 文本。</li>
 * </ol>
 */
class GoodsSpuMapperXmlTest {

	private static final String MAPPER_RESOURCE = "mapper/GoodsSpuMapper.xml";

	private static final String STATEMENT_ID = "com.aryn.cloud.product.mapper.GoodsSpuMapper.selectApiPage";

	private static final String RECOMMEND_STATEMENT_ID =
			"com.aryn.cloud.product.mapper.GoodsSpuMapper.selectRecommendPage";

	private static MappedStatement selectApiPage;

	private static MappedStatement selectRecommendPage;

	private static PaginationInnerInterceptor paginationInterceptor;

	@BeforeAll
	static void parseMapperXml() throws Exception {
		Configuration configuration = new Configuration();
		try (InputStream inputStream = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
			XMLMapperBuilder builder = new XMLMapperBuilder(inputStream, configuration, MAPPER_RESOURCE,
					configuration.getSqlFragments());
			builder.parse();
		}
		selectApiPage = configuration.getMappedStatement(STATEMENT_ID);
		selectRecommendPage = configuration.getMappedStatement(RECOMMEND_STATEMENT_ID);
		paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
	}

	private static String recommendPageSql(List<String> excludeIds, List<String> categorySecondIds,
			List<String> categoryFirstIds) {
		Map<String, Object> params = new HashMap<>(4);
		params.put("excludeIds", excludeIds);
		params.put("categorySecondIds", categorySecondIds);
		params.put("categoryFirstIds", categoryFirstIds);
		BoundSql boundSql = selectRecommendPage.getBoundSql(params);
		return normalize(boundSql.getSql());
	}

	private static String apiPageSql(BigDecimal bargainRate) {
		Map<String, Object> params = new HashMap<>(2);
		params.put("query", new GoodsSpu());
		params.put("bargainRate", bargainRate);
		BoundSql boundSql = selectApiPage.getBoundSql(params);
		return normalize(boundSql.getSql());
	}

	private static String normalize(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}

	@Test
	void selectApiPageWithoutBargainRateKeepsLegacySql() {
		String sql = apiPageSql(null);

		// 存量零影响：不携带阈值时不得出现 bargain 过滤，也不得由 XML 输出 ORDER BY（排序仍来自 Page.orders）
		assertThat(sql).doesNotContain("original_price >");
		assertThat(sql).doesNotContain("bargainRate");
		assertThat(sql).doesNotContainIgnoringCase("order by");
		// 既有过滤条件原样保留，避免改动顺手改变了 C 端商品池口径
		assertThat(sql).contains("goods_spu.`del_flag` = '0'");
		assertThat(sql).contains("goods_category.`del_flag` = '0'");
		assertThat(sql).contains("goods_spu.`status` = '1'");
	}

	@Test
	void selectApiPageWithBargainRateAppendsFilterAndFixedOrder() {
		String sql = apiPageSql(new BigDecimal("1.15"));

		// 圈品口径：原价非空且 > 0（防除零），原价 >= 售价 × 阈值
		assertThat(sql).contains("AND goods_spu.original_price IS NOT NULL");
		assertThat(sql).contains("AND goods_spu.original_price > 0");
		assertThat(sql).contains("AND goods_spu.original_price >= goods_spu.sales_price * ?");
		// 固定排序：折扣率降序、售价升序、id 降序兜底（tie-break 保证跨页稳定）
		assertThat(sql).contains(
				"ORDER BY ((goods_spu.original_price - goods_spu.sales_price) / goods_spu.original_price) DESC,"
						+ " goods_spu.sales_price ASC, goods_spu.id DESC");
		// 阈值只以绑定值出现：SQL 文本里既没有阈值字面量，也没有参数名（无拼接 = 无注入面）
		assertThat(sql).doesNotContain("1.15");
		assertThat(sql).doesNotContain("bargainRate");
	}

	@Test
	void countSqlStripsFixedOrderByButKeepsBargainFilter() {
		// MyBatis-Plus 分页插件的 count 查询：必须先成功解析带固定 ORDER BY 的 SQL，再剥掉 ORDER BY；
		// 若解析失败会降级成「子查询包一层」（ORDER BY 仍在），本断言即回归点。
		IPage<GoodsSpu> page = new Page<>(1, 10);
		String countSql = normalize(paginationInterceptor.autoCountSql(page, apiPageSql(new BigDecimal("1.15"))));

		assertThat(countSql).containsIgnoringCase("count(");
		assertThat(countSql).doesNotContainIgnoringCase("order by");
		assertThat(countSql).contains("goods_spu.original_price >= goods_spu.sales_price * ?");
	}

	@Test
	void legacyAscDescStillAppendedByPaginationPluginWhenNoBargainRate() {
		// 不携带阈值时排序仍由分页插件从 Page.orders 追加：controller 的 asc/desc 参数链路未被破坏
		String withOrder = normalize(
				paginationInterceptor.concatOrderBy(apiPageSql(null), List.of(OrderItem.asc("sales_price"))));

		assertThat(withOrder).containsIgnoringCase("order by sales_price ASC");
	}

	@Test
	void selectRecommendPageExcludesContextGoodsAndPrioritizesCategories() {
		String sql = recommendPageSql(List.of("spu-1", "spu-9"),
				List.of("cat-2a", "cat-2b"), List.of("cat-1"));

		// 排除当前商品/购物车或订单内商品；同二级分类权重 0、同一级分类权重 1、其余 2
		assertThat(sql).contains("AND goods_spu.id NOT IN");
		assertThat(sql).containsPattern("ORDER BY CASE WHEN goods_spu\\.category_second_id IN"
				+ " \\(\\s*\\?\\s*,\\s*\\?\\s*\\) THEN 0"
				+ " WHEN goods_spu\\.category_first_id IN \\(\\s*\\?\\s*\\) THEN 1 ELSE 2 END,");
		// 分类之后按销量、上架时间、id 兜底，保证翻页顺序稳定
		assertThat(sql).contains("goods_spu.sales_volume DESC, goods_spu.create_time DESC, goods_spu.id DESC");
		// 只保留在售商品；用户输入只以绑定值出现，不落入 SQL 文本
		assertThat(sql).contains("goods_spu.`status` = '1'");
		assertThat(sql).doesNotContain("spu-1").doesNotContain("spu-9")
			.doesNotContain("cat-2a").doesNotContain("cat-1");
	}

	@Test
	void selectRecommendPageWithoutContextUsesGlobalOrdering() {
		String sql = recommendPageSql(List.of(), List.of(), List.of());

		assertThat(sql).doesNotContain("NOT IN");
		assertThat(sql).doesNotContain("CASE WHEN");
		assertThat(sql).contains("ORDER BY goods_spu.sales_volume DESC,"
				+ " goods_spu.create_time DESC, goods_spu.id DESC");
	}

}
