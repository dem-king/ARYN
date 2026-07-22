package com.aryn.cloud.boot.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TenantJoinAliasAuditTest {

	private static final Pattern MAPPER_SELECT = Pattern.compile(
			"(?is)<select\\b[^>]*\\bid=\"([^\"]+)\"[^>]*>(.*?)</select>");

	private static final Pattern MAPPER_SQL_FRAGMENT = Pattern.compile(
			"(?is)<sql\\b[^>]*\\bid=\"([^\"]+)\"[^>]*>(.*?)</sql>");

	private static final Pattern MAPPER_INCLUDE = Pattern.compile(
			"(?is)<include\\b[^>]*\\brefid=\"([^\"]+)\"[^>]*/\\s*>");

	private static final Pattern MYBATIS_PARAMETER = Pattern.compile("#\\{[^}]+}");

	private static final Pattern SINGLE_QUOTED_VALUE = Pattern.compile("(?s)'(?:''|\\\\.|[^'])*'");

	private static final Pattern XML_TAG = Pattern.compile("(?s)<[^>]+>");

	private static final Pattern TOKEN = Pattern.compile("(?i)[a-z_][a-z0-9_]*|[(),.]");

	private static final Set<String> SQL_KEYWORDS = Set.of("ALL", "AND", "AS", "ASC", "BY", "CROSS",
			"DESC", "DISTINCT", "FOR", "FORCE", "FULL", "GROUP", "HAVING", "IGNORE", "INNER", "JOIN",
			"LEFT", "LIMIT", "LOCK", "NATURAL", "ON", "OR", "ORDER", "OUTER", "PARTITION", "RIGHT", "SET",
			"STRAIGHT_JOIN", "UNION", "USE", "USING", "WHERE");

	private static final Set<String> FROM_CLAUSE_TERMINATORS = Set.of("FOR", "GROUP", "HAVING", "INTO",
			"LIMIT", "LOCK", "ORDER", "PROCEDURE", "QUALIFY", "UNION", "WHERE", "WINDOW");

	private final Path projectRoot = findProjectRoot();

	@Test
	void rejectsUnaliasedTenantTablesInSameJoinScope() {
		String sql = "SELECT * FROM message_recipient "
				+ "INNER JOIN message_notice ON message_notice.id = message_recipient.message_id";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("message_recipient", "message_notice");
	}

	@Test
	void acceptsAliasedTenantTablesInSameJoinScope() {
		String sql = "SELECT * FROM message_recipient AS message_recipient "
				+ "INNER JOIN message_notice AS message_notice "
				+ "ON message_notice.id = message_recipient.message_id";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice"))).isEmpty();
	}

	@Test
	void rejectsUnaliasedTenantTablesUsingStraightJoin() {
		String sql = "SELECT * FROM message_recipient "
				+ "STRAIGHT_JOIN message_notice ON message_notice.id = message_recipient.message_id";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("message_recipient", "message_notice");
	}

	@Test
	void rejectsUnaliasedTenantTablesUsingJoinUsingClause() {
		String sql = "SELECT * FROM message_recipient "
				+ "INNER JOIN message_notice USING (message_id)";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("message_recipient", "message_notice");
	}

	@Test
	void rejectsUnaliasedTenantTablesUsingNaturalJoin() {
		String sql = "SELECT * FROM message_recipient NATURAL JOIN message_notice";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("message_recipient", "message_notice");
	}

	@Test
	void rejectsUnaliasedTenantTablesUsingCommaJoin() {
		String sql = "SELECT * FROM message_recipient, message_notice "
				+ "WHERE message_notice.id = message_recipient.message_id";

		assertThat(missingAliases(sql, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("message_recipient", "message_notice");
	}

	@Test
	void ignoresUnaliasedSingleTableSubqueriesJoinedByOuterDerivedAliases() {
		String sql = "SELECT * FROM (SELECT * FROM order_info) o "
				+ "LEFT JOIN (SELECT * FROM order_refund) r ON 1 = 1";

		assertThat(missingAliases(sql, Set.of("order_info", "order_refund"))).isEmpty();
	}

	@Test
	void expandsSqlFragmentsBeforeAuditingAliases() {
		String xml = """
				<mapper namespace="test.Mapper">
				    <sql id="joinedTables">
				        FROM message_recipient
				        INNER JOIN message_notice ON message_notice.id = message_recipient.message_id
				    </sql>
				    <select id="selectInbox" resultType="java.lang.Long">
				        SELECT COUNT(1)
				        <include refid="joinedTables"/>
				    </select>
				</mapper>
				""";

		assertThat(mapperViolations(xml, Set.of("message_recipient", "message_notice")))
				.containsExactlyInAnyOrder("selectInbox:message_recipient", "selectInbox:message_notice");
	}

	@Test
	void tenantInterceptorQualifiesInjectedPredicatesForActualUnreadQuery() throws IOException {
		Path mapper = projectRoot.resolve(
				"aryn-message/aryn-message-biz/src/main/resources/mapper/MessageRecipientMapper.xml");
		String xml = Files.readString(mapper);
		String sql = executableSql(mapperStatement(xml, "countUnread"), sqlFragments(xml));
		TenantLineHandler handler = new TenantLineHandler() {
			@Override
			public Expression getTenantId() {
				return new StringValue("tenant-1");
			}

			@Override
			public boolean ignoreTable(String tableName) {
				return !("message_recipient".equalsIgnoreCase(tableName)
						|| "message_notice".equalsIgnoreCase(tableName));
			}
		};

		String rewrittenSql = new TenantLineInnerInterceptor(handler).parserSingle(sql, null)
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);

		assertThat(rewrittenSql).contains("message_recipient.tenant_id = 'tenant-1'",
				"message_notice.tenant_id = 'tenant-1'");
	}

	@Test
	void allTenantTablesInJoinScopesUseExplicitAliases() throws IOException {
		Set<String> tenantTables = tenantTablesFromBootYaml();
		List<String> violations = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(projectRoot)) {
			for (Path path : paths.filter(TenantJoinAliasAuditTest::isMapperXml).toList()) {
				String xml = Files.readString(path);
				for (String violation : mapperViolations(xml, tenantTables)) {
					violations.add(relative(path) + "#" + violation);
				}
			}
		}

		assertThat(violations).as("多租户表参与 JOIN 时必须声明表别名").isEmpty();
	}

	private static Set<String> missingAliases(String sql, Set<String> tenantTables) {
		String lexicalSql = XML_TAG.matcher(SINGLE_QUOTED_VALUE.matcher(sql).replaceAll("''")).replaceAll(" ");
		List<Token> tokens = tokenize(lexicalSql);
		Set<String> missing = new LinkedHashSet<>();
		for (QueryRange query : queryRanges(tokens)) {
			if (!containsMultipleTableSources(tokens, query)) {
				continue;
			}
			boolean inFromClause = false;
			for (int index = query.start() + 1; index < query.end(); index++) {
				Token token = tokens.get(index);
				if (token.depth() != query.depth()) {
					continue;
				}
				if (token.is("FROM")) {
					inFromClause = true;
				} else if (inFromClause && isFromClauseTerminator(token)) {
					inFromClause = false;
					continue;
				}
				if (!(token.is("FROM") || (inFromClause && (isJoin(token) || token.is(","))))) {
					continue;
				}
				int tableIndex = nextAtDepth(tokens, index + 1, query.end(), query.depth());
				if (tableIndex < 0 || tokens.get(tableIndex).is("(")) {
					continue;
				}
				int tableNameIndex = qualifiedTableNameIndex(tokens, tableIndex, query.end(), query.depth());
				String table = tokens.get(tableNameIndex).normalized();
				if (!tenantTables.contains(table)) {
					continue;
				}
				int aliasIndex = nextAtDepth(tokens, tableNameIndex + 1, query.end(), query.depth());
				if (aliasIndex >= 0 && tokens.get(aliasIndex).is("AS")) {
					aliasIndex = nextAtDepth(tokens, aliasIndex + 1, query.end(), query.depth());
				}
				if (aliasIndex < 0 || !isAlias(tokens.get(aliasIndex))) {
					missing.add(table);
				}
			}
		}
		return missing;
	}

	private static List<String> mapperViolations(String xml, Set<String> tenantTables) {
		Map<String, String> fragments = sqlFragments(xml);
		List<String> violations = new ArrayList<>();
		Matcher statements = MAPPER_SELECT.matcher(xml);
		while (statements.find()) {
			String statementId = statements.group(1);
			String expandedSql = expandIncludes(statements.group(2), fragments, new LinkedHashSet<>());
			for (String table : missingAliases(expandedSql, tenantTables)) {
				violations.add(statementId + ":" + table);
			}
		}
		return violations;
	}

	private static Map<String, String> sqlFragments(String xml) {
		Map<String, String> fragments = new LinkedHashMap<>();
		Matcher matcher = MAPPER_SQL_FRAGMENT.matcher(xml);
		while (matcher.find()) {
			fragments.put(matcher.group(1), matcher.group(2));
		}
		return fragments;
	}

	private static String mapperStatement(String xml, String statementId) {
		Matcher statements = MAPPER_SELECT.matcher(xml);
		while (statements.find()) {
			if (statementId.equals(statements.group(1))) {
				return statements.group(2);
			}
		}
		throw new IllegalStateException("无法定位 Mapper 查询: " + statementId);
	}

	private static String executableSql(String sql, Map<String, String> fragments) {
		String expanded = expandIncludes(sql, fragments, new LinkedHashSet<>());
		String withoutTags = XML_TAG.matcher(expanded).replaceAll(" ");
		String decoded = withoutTags.replace("&gt;", ">")
				.replace("&lt;", "<")
				.replace("&amp;", "&");
		return MYBATIS_PARAMETER.matcher(decoded).replaceAll("'request-value'");
	}

	private static String expandIncludes(String sql, Map<String, String> fragments, Set<String> resolving) {
		Matcher includes = MAPPER_INCLUDE.matcher(sql);
		StringBuffer expanded = new StringBuffer();
		while (includes.find()) {
			String refid = includes.group(1);
			String localRefid = refid.substring(refid.lastIndexOf('.') + 1);
			String fragment = fragments.get(localRefid);
			if (fragment == null) {
				throw new IllegalStateException("无法解析 Mapper SQL 片段: " + refid);
			}
			if (!resolving.add(localRefid)) {
				throw new IllegalStateException("Mapper SQL 片段循环引用: " + localRefid);
			}
			String replacement = expandIncludes(fragment, fragments, resolving);
			resolving.remove(localRefid);
			includes.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
		}
		includes.appendTail(expanded);
		return expanded.toString();
	}

	private static boolean containsMultipleTableSources(List<Token> tokens, QueryRange query) {
		boolean inFromClause = false;
		int tableSources = 0;
		for (int index = query.start() + 1; index < query.end(); index++) {
			Token token = tokens.get(index);
			if (token.depth() != query.depth()) {
				continue;
			}
			if (token.is("FROM")) {
				inFromClause = true;
				tableSources++;
			} else if (inFromClause && isFromClauseTerminator(token)) {
				inFromClause = false;
			} else if (inFromClause && (isJoin(token) || token.is(","))) {
				tableSources++;
			}
			if (tableSources > 1) {
				return true;
			}
		}
		return false;
	}

	private static boolean isFromClauseTerminator(Token token) {
		return FROM_CLAUSE_TERMINATORS.contains(token.value().toUpperCase(Locale.ROOT));
	}

	private static boolean isJoin(Token token) {
		return token.is("JOIN") || token.is("STRAIGHT_JOIN");
	}

	private static int qualifiedTableNameIndex(List<Token> tokens, int tableIndex, int end, int depth) {
		int dotIndex = nextAtDepth(tokens, tableIndex + 1, end, depth);
		if (dotIndex >= 0 && tokens.get(dotIndex).is(".")) {
			int qualifiedNameIndex = nextAtDepth(tokens, dotIndex + 1, end, depth);
			if (qualifiedNameIndex >= 0) {
				return qualifiedNameIndex;
			}
		}
		return tableIndex;
	}

	private static boolean isAlias(Token token) {
		return token.identifier() && !SQL_KEYWORDS.contains(token.value().toUpperCase(Locale.ROOT));
	}

	private static int nextAtDepth(List<Token> tokens, int start, int end, int depth) {
		for (int index = start; index < end; index++) {
			if (tokens.get(index).depth() == depth) {
				return index;
			}
		}
		return -1;
	}

	private static List<QueryRange> queryRanges(List<Token> tokens) {
		List<QueryRange> ranges = new ArrayList<>();
		for (int index = 0; index < tokens.size(); index++) {
			Token token = tokens.get(index);
			if (!token.is("SELECT")) {
				continue;
			}
			int end = tokens.size();
			for (int candidate = index + 1; candidate < tokens.size(); candidate++) {
				Token next = tokens.get(candidate);
				if ((next.is(")") && next.depth() < token.depth())
						|| (next.is("UNION") && next.depth() == token.depth())) {
					end = candidate;
					break;
				}
			}
			ranges.add(new QueryRange(index, end, token.depth()));
		}
		return ranges;
	}

	private static List<Token> tokenize(String sql) {
		List<Token> tokens = new ArrayList<>();
		Matcher matcher = TOKEN.matcher(sql);
		int depth = 0;
		while (matcher.find()) {
			String value = matcher.group();
			if (")".equals(value)) {
				depth = Math.max(0, depth - 1);
			}
			tokens.add(new Token(value, depth));
			if ("(".equals(value)) {
				depth++;
			}
		}
		return tokens;
	}

	@SuppressWarnings("unchecked")
	private Set<String> tenantTablesFromBootYaml() throws IOException {
		String yamlContent = Files.readString(projectRoot.resolve("aryn-boot/src/main/resources/application.yml"));
		Matcher hxSection = Pattern.compile("(?m)^hx:\\s*$").matcher(yamlContent);
		assertThat(hxSection.find()).as("hx.tenant 配置段").isTrue();
		Map<String, Object> root = new Yaml().load(yamlContent.substring(hxSection.start()));
		Map<String, Object> hx = (Map<String, Object>) root.get("hx");
		Map<String, Object> tenant = (Map<String, Object>) hx.get("tenant");
		List<Object> tables = (List<Object>) tenant.get("tables");
		Set<String> result = new LinkedHashSet<>();
		for (Object table : tables) {
			result.add(String.valueOf(table).trim().toLowerCase(Locale.ROOT));
		}
		return result;
	}

	private static boolean isMapperXml(Path path) {
		String normalized = path.toString().replace('\\', '/');
		return normalized.contains("/src/main/resources/mapper/") && normalized.endsWith(".xml");
	}

	private String relative(Path path) {
		return projectRoot.relativize(path).toString().replace('\\', '/');
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("无法定位 aryn-mall-java 根目录");
		}
		return current;
	}

	private record QueryRange(int start, int end, int depth) {
	}

	private record Token(String value, int depth) {

		private boolean identifier() {
			return Character.isLetter(value.charAt(0)) || value.charAt(0) == '_';
		}

		private boolean is(String expected) {
			return value.equalsIgnoreCase(expected);
		}

		private String normalized() {
			return value.toLowerCase(Locale.ROOT);
		}

	}

}
