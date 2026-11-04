package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 {@code ZoneGoodsQueryMapper.xml} 的 SQL 契约（不连数据库，直接解析 mapper XML 生成 BoundSql）。
 *
 * <p>回归重点：专区商品分页/取 ID 必须固定 {@code ORDER BY goods_spu.id ASC}（G16：
 * 无排序时分页遍历顺序不稳定，翻页收集 ID 可能漏/重）；新增 not_member/not_clearance 过滤；
 * limit 只作为绑定值不拼进 SQL 文本。
 */
class ZoneGoodsQueryMapperXmlTest {

	private static final String MAPPER_RESOURCE = "mapper/ZoneGoodsQueryMapper.xml";

	private static final String NAMESPACE = "com.aryn.cloud.product.mapper.ZoneGoodsQueryMapper";

	private static Configuration configuration;

	@BeforeAll
	static void parseMapperXml() throws Exception {
		configuration = new Configuration();
		try (InputStream inputStream = Resources.getResourceAsStream(MAPPER_RESOURCE)) {
			XMLMapperBuilder builder = new XMLMapperBuilder(inputStream, configuration, MAPPER_RESOURCE,
					configuration.getSqlFragments());
			builder.parse();
		}
	}

	@Test
	@DisplayName("专区分页：member 过滤 + 固定 id 升序")
	void zonePageHasMemberFilterAndStableOrder() {
		String sql = boundSql("selectZonePage", Map.of("query", zoneQuery("member")));

		assertThat(sql).contains("is_member_item");
		assertThat(sql).containsIgnoringCase("ORDER BY goods_spu.`id` ASC");
		// 分页 LIMIT 由 MyBatis-Plus PaginationInnerInterceptor 追加，不写在 XML 里
		assertThat(sql).doesNotContain("LIMIT");
	}

	@Test
	@DisplayName("专区分页：clearance 过滤")
	void zonePageHasClearanceFilter() {
		String sql = boundSql("selectZonePage", Map.of("query", zoneQuery("clearance")));

		assertThat(sql).contains("is_clearance_item");
	}

	@Test
	@DisplayName("取 ID：not_member/not_clearance 支持，limit 仅作为绑定值")
	void idsSupportsNotZoneTypesAndBoundLimit() {
		ZoneGoodsIdsDTO notMember = new ZoneGoodsIdsDTO();
		notMember.setZoneType("not_member");
		String sql = boundSql("selectZoneIds", Map.of("query", notMember, "limit", 500));

		assertThat(sql).contains("`is_member_item` IS NULL");
		assertThat(sql).containsIgnoringCase("ORDER BY goods_spu.`id` ASC");
		assertThat(sql).contains("LIMIT ?");
		assertThat(sql).doesNotContain("LIMIT 500");

		ZoneGoodsIdsDTO notClearance = new ZoneGoodsIdsDTO();
		notClearance.setZoneType("not_clearance");
		String sql2 = boundSql("selectZoneIds", Map.of("query", notClearance, "limit", 1000));
		assertThat(sql2).contains("`is_clearance_item` IS NULL");
	}

	@Test
	@DisplayName("取 ID 计数：与 selectZoneIds 同 where 口径，无 ORDER BY/LIMIT")
	void countIdsSharesFilter() {
		ZoneGoodsIdsDTO query = new ZoneGoodsIdsDTO();
		query.setZoneType("member");
		query.setStatus("1");
		String sql = boundSql("countZoneIds", Map.of("query", query));

		assertThat(sql).contains("COUNT(*)");
		assertThat(sql).contains("is_member_item");
		assertThat(sql).containsIgnoringCase("status");
		assertThat(sql).doesNotContainIgnoringCase("ORDER BY");
		assertThat(sql).doesNotContainIgnoringCase("LIMIT");
	}

	@Test
	@DisplayName("SKU 覆盖查询：按 spuIds 批量 IN，租户条件交给拦截器")
	void skuRowsUsesInList() {
		String sql = boundSql("selectSkuZoneRows", Map.of("spuIds", List.of("spu-1", "spu-2")));

		assertThat(sql).contains("goods_sku.`del_flag` = '0'");
		assertThat(sql).contains("goods_sku.`spu_id` IN");
	}

	private String boundSql(String statementId, Map<String, Object> params) {
		MappedStatement statement = configuration.getMappedStatement(NAMESPACE + "." + statementId);
		BoundSql boundSql = statement.getBoundSql(new HashMap<>(params));
		return normalize(boundSql.getSql());
	}

	private String normalize(String sql) {
		return sql.replaceAll("\\s+", " ").trim();
	}

	private ZoneGoodsQueryDTO zoneQuery(String zoneType) {
		ZoneGoodsQueryDTO query = new ZoneGoodsQueryDTO();
		query.setZoneType(zoneType);
		query.setCurrent(1);
		query.setSize(10);
		return query;
	}

}
