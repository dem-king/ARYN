package com.aryn.cloud.boot.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义 Mapper 方法与语句绑定审计。
 * <p>
 * 覆盖两类运行时才暴露的问题：
 * <ol>
 * <li>自定义方法只声明了接口而未写 XML 语句/注解 —— 运行时抛 {@code Invalid bound statement}；</li>
 * <li>自定义方法名与 {@link BaseMapper} 内置方法重名 —— MyBatis 的语句 id 是「命名空间 + 方法名」，
 * XML/注解会以同名 id 抢在 MyBatis-Plus 注入之前注册（MP 3.5.15 的
 * {@code AbstractMethod#hasMappedStatement} 会让注入直接跳过），导致内置方法被自定义 SQL 顶掉。
 * 反例（2026-10-28 修复）：{@code SysUserMapper#selectCount(@Param("query") SysUser)}
 * 与内置 {@code selectCount(Wrapper)} 冲突，XML 抢先注册后，任何 {@code sysUserService.count()}
 * 都会按自定义 SQL 绑定参数，抛 {@code Parameter 'query' not found. Available parameters are [ew, param1]}；
 * 租户管理「详情」的概览接口正是踩中这条路径。自定义方法必须改用不重名的名字。</li>
 * </ol>
 */
class MapperStatementBindingAuditTest {

	/** BaseMapper 已提供实现的方法名（按当前 MP 版本反射获取，避免手写清单随版本漂移） */
	private static final Set<String> BASE_MAPPER_METHODS = Arrays.stream(BaseMapper.class.getMethods())
		.map(Method::getName)
		.collect(Collectors.toUnmodifiableSet());

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

	/**
	 * 自定义方法不得与 BaseMapper 内置方法重名。
	 * <p>
	 * 同名时语句 id 相同，XML/注解会覆盖 MyBatis-Plus 注入的内置语句；而自定义方法的参数名
	 * （如 {@code @Param("query")}）与内置方法（{@code @Param("ew")} + 位置参数 {@code param1}）
	 * 不一致，调用内置方法就会抛 BindingException。要自定义 SQL 请改用不重名的方法名。
	 */
	@Test
	void customMapperMethodsMustNotShadowBaseMapperMethods() throws IOException {
		List<Path> mapperInterfaces = mapperInterfaces();
		assertThat(mapperInterfaces).as("扫描到的 Mapper 接口").isNotEmpty();

		List<String> shadowed = new ArrayList<>();
		for (Path mapperInterface : mapperInterfaces) {
			String source = Files.readString(mapperInterface);
			Matcher declaration = INTERFACE_DECLARATION.matcher(source);
			if (!declaration.find()) {
				continue;
			}
			for (MethodDeclaration method : declarations(source, declaration.group(1))) {
				if (BASE_MAPPER_METHODS.contains(method.name())) {
					shadowed.add(relative(mapperInterface) + "#" + method.name());
				}
			}
		}

		assertThat(shadowed)
			.as("Mapper 接口自定义方法不得与 BaseMapper 内置方法重名，否则 XML/注解会覆盖内置语句导致 "
					+ "BindingException。请给自定义方法换一个不重名的名字")
			.isEmpty();
	}

	private List<Path> mapperInterfaces() throws IOException {
		List<Path> mapperInterfaces = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(projectRoot)) {
			paths.filter(MapperStatementBindingAuditTest::isMapperInterface).forEach(mapperInterfaces::add);
		}
		return mapperInterfaces;
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
