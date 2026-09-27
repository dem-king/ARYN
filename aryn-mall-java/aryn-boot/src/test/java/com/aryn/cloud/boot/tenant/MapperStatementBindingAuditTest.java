package com.aryn.cloud.boot.tenant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义 Mapper 方法必须能被 MyBatis 解析到语句：要么 XML 中有同名 statement，要么方法上有注解。
 * <p>
 * 反例：SocialUserMapper 的跨租户反查方法只声明了接口而未写 XML 语句，单元测试用 Mockito 直接
 * mock 掉 mapper，编译期与单测都发现不了，直到运行时调用才抛 Invalid bound statement。
 */
class MapperStatementBindingAuditTest {

	/** BaseMapper 已提供实现，无需自定义语句 */
	private static final Set<String> BASE_MAPPER_METHODS = Set.of("insert", "deleteById", "deleteByMap", "delete",
			"deleteBatchIds", "updateById", "update", "updateBatchById", "selectById", "selectBatchIds",
			"selectByMap", "selectOne", "selectCount", "selectList", "selectMaps", "selectMapsPage", "selectObjs",
			"selectPage", "exists");

	private static final Pattern MAPPER_NAMESPACE = Pattern.compile("<mapper\\s+namespace=\"([^\"]+)\"");

	private static final Pattern XML_STATEMENT = Pattern.compile(
			"<(?:select|insert|update|delete|sql)\\s+id=\"([^\"]+)\"");

	private static final Pattern INTERFACE_DECLARATION = Pattern.compile("public\\s+interface\\s+(\\w+)");

	/**
	 * 方法声明：返回类型 + 名称 + 参数列表，以分号结束。
	 * <p>
	 * 参数列表中含 @Param("...") 这类带括号的注解，因此不能把括号排除在参数匹配之外，
	 * 否则方法全部匹配不到，该审计会退化成永远通过。
	 */
	private static final Pattern METHOD_DECLARATION = Pattern.compile(
			"(?m)^\\s*(?!//|/\\*|\\*)(?:public\\s+)?[\\w<>\\[\\],\\.\\s\\?]+?\\s+(\\w+)\\s*\\([^;{]*?\\)\\s*;");

	private static final Pattern METHOD_ANNOTATION = Pattern.compile(
			"@(?:Select|Insert|Update|Delete|SelectProvider|InsertProvider|UpdateProvider|DeleteProvider)\\b");

	private final Path projectRoot = findProjectRoot();

	@Test
	void everyCustomMapperMethodIsBoundToAnXmlStatementOrAnnotation() throws IOException {
		List<Path> mapperInterfaces = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(projectRoot)) {
			paths.filter(MapperStatementBindingAuditTest::isMapperInterface).forEach(mapperInterfaces::add);
		}
		assertThat(mapperInterfaces).as("扫描到的 Mapper 接口").isNotEmpty();

		List<String> unbound = new ArrayList<>();
		for (Path mapperInterface : mapperInterfaces) {
			String source = Files.readString(mapperInterface);
			Matcher declaration = INTERFACE_DECLARATION.matcher(source);
			if (!declaration.find()) {
				continue;
			}
			Set<String> xmlStatements = xmlStatementsFor(declaration.group(1));

			for (MethodDeclaration method : declarations(source, declaration.group(1))) {
				if (BASE_MAPPER_METHODS.contains(method.name()) || xmlStatements.contains(method.name())) {
					continue;
				}
				if (method.annotated()) {
					continue;
				}
				unbound.add(relative(mapperInterface) + "#" + method.name());
			}
		}

		assertThat(unbound).as("自定义 Mapper 方法必须绑定 XML 语句或注解，否则运行时报 Invalid bound statement")
			.isEmpty();
	}

	private Set<String> xmlStatementsFor(String interfaceName) throws IOException {
		Set<String> statements = new TreeSet<>();
		try (Stream<Path> paths = Files.walk(projectRoot)) {
			for (Path path : paths.filter(MapperStatementBindingAuditTest::isMapperXml).toList()) {
				String xml = Files.readString(path);
				Matcher namespace = MAPPER_NAMESPACE.matcher(xml);
				if (!namespace.find() || !namespace.group(1).endsWith("." + interfaceName)) {
					continue;
				}
				Matcher statement = XML_STATEMENT.matcher(xml);
				while (statement.find()) {
					statements.add(statement.group(1));
				}
			}
		}
		return statements;
	}

	/**
	 * 解析接口方法声明。注解位于签名之前，且多行文本块注解长度不定，
	 * 因此取“上一个方法声明结束位置”到当前签名之间的区间判断注解，不用固定长度回看窗口。
	 */
	private static List<MethodDeclaration> declarations(String source, String interfaceName) {
		int bodyStart = source.indexOf(interfaceName);
		String body = bodyStart < 0 ? source : source.substring(bodyStart);
		List<MethodDeclaration> declarations = new ArrayList<>();
		Matcher matcher = METHOD_DECLARATION.matcher(body);
		int previousEnd = 0;
		while (matcher.find()) {
			boolean annotated = METHOD_ANNOTATION.matcher(body.substring(previousEnd, matcher.start())).find();
			declarations.add(new MethodDeclaration(matcher.group(1), annotated));
			previousEnd = matcher.end();
		}
		return declarations;
	}

	private record MethodDeclaration(String name, boolean annotated) {
	}

	private static boolean isMapperInterface(Path path) {
		String normalized = path.toString().replace('\\', '/');
		return normalized.contains("/src/main/java/") && normalized.endsWith("Mapper.java");
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

}
