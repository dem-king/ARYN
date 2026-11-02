package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.GoodsAppraise;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 {@code GoodsAppraiseMapper.xml} 的评价增强契约（不连数据库，直接解析 mapper XML 并生成 BoundSql）：
 * <ol>
 * <li>数量统计排除系统默认评价（{@code appraise_type=1}），默认评价不进好评率分子/分母；</li>
 * <li>分页查询补齐匿名/标签/追评等新字段，并支持 userId/orderId/spuId/appraiseType 可选筛选；</li>
 * <li>所有筛选条件只以绑定值（{@code ?}）出现，不拼接 SQL 文本。</li>
 * </ol>
 */
class GoodsAppraiseMapperXmlTest {

	private static final String MAPPER_RESOURCE = "mapper/GoodsAppraiseMapper.xml";

	private static final String COUNT_STATEMENT_ID = "com.aryn.cloud.product.mapper.GoodsAppraiseMapper.selectGoodsAppraiseCount";

	private static final String PAGE_STATEMENT_ID = "com.aryn.cloud.product.mapper.GoodsAppraiseMapper.selectAppraisePage";

	private static MappedStatement countStatement;

	private static MappedStatement pageStatement;

	@BeforeAll
	static void parseMapperXml() throws Exception {
		Configuration configuration = new Configuration();
		try (InputStream inputStream = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
			XMLMapperBuilder builder = new XMLMapperBuilder(inputStream, configuration, MAPPER_RESOURCE,
					configuration.getSqlFragments());
			builder.parse();
		}
		countStatement = configuration.getMappedStatement(COUNT_STATEMENT_ID);
		pageStatement = configuration.getMappedStatement(PAGE_STATEMENT_ID);
	}

	@Test
	void countExcludesSystemDefaultAppraise() {
		String sql = countSql(new GoodsAppraise());

		assertThat(sql).contains("goods_appraise.del_flag='0'");
		// 默认评价不进好评率分子/分母
		assertThat(sql).contains("goods_appraise.appraise_type = 0");
		assertThat(sql).contains("goods_appraise.goods_score >= 4");
	}

	@Test
	void countBindsSpuAndUserFiltersWithoutSqlLiteral() {
		String sql = countSql(new GoodsAppraise().setSpuId("spu-filter").setUserId("user-filter"));

		assertThat(sql).contains("goods_appraise.`spu_id` = ?").contains("goods_appraise.`user_id` = ?");
		assertThat(sql).doesNotContain("spu-filter").doesNotContain("user-filter");
	}

	@Test
	void pageSelectsEnhancementColumnsAndKeepsLogicalDeleteFilter() {
		String sql = pageSql(new GoodsAppraise());

		assertThat(sql).contains("LEFT JOIN goods_spu");
		assertThat(sql).contains("goods_appraise.appraise_type").contains("goods_appraise.is_anonymous")
			.contains("goods_appraise.tags").contains("goods_appraise.advance_content")
			.contains("goods_appraise.advance_pic_urls").contains("goods_appraise.advance_time")
			.contains("goods_appraise.modify_time");
		assertThat(sql).contains("goods_appraise.del_flag = '0'");
		// 「我的评价」/管理端均按最新在前，避免分页顺序漂移
		assertThat(sql).contains("ORDER BY goods_appraise.create_time DESC, goods_appraise.id DESC");
		// 未传筛选条件时不追加可选条件
		assertThat(sql).doesNotContain("goods_appraise.order_id = ?").doesNotContain("goods_appraise.appraise_type = ?");
	}

	@Test
	void pageBindsOptionalFiltersWithoutSqlLiteral() {
		GoodsAppraise query = new GoodsAppraise().setUserId("user-filter").setOrderId("order-filter")
			.setSpuId("spu-filter").setNickname("nickname-filter").setAppraiseType(1);
		String sql = pageSql(query);

		assertThat(sql).contains("goods_appraise.nickname = ?").contains("goods_appraise.user_id = ?")
			.contains("goods_appraise.order_id = ?").contains("goods_appraise.spu_id = ?")
			.contains("goods_appraise.appraise_type = ?");
		assertThat(sql).doesNotContain("user-filter").doesNotContain("order-filter").doesNotContain("spu-filter")
			.doesNotContain("nickname-filter");
	}

	private static String countSql(GoodsAppraise query) {
		return boundSql(countStatement, query);
	}

	private static String pageSql(GoodsAppraise query) {
		return boundSql(pageStatement, query);
	}

	private static String boundSql(MappedStatement statement, GoodsAppraise query) {
		Map<String, Object> params = new HashMap<>(2);
		params.put("query", query);
		BoundSql boundSql = statement.getBoundSql(params);
		return boundSql.getSql().replaceAll("\\s+", " ").trim();
	}

}
