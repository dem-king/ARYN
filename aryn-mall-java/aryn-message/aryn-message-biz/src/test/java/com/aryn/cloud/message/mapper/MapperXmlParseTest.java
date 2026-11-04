package com.aryn.cloud.message.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper XML 启动期解析守卫。
 *
 * <p>客服加固新增了 {@code <sql>} 片段嵌套 {@code <choose>/<include>}、参与者快照 JOIN 与
 * 复合游标条件；这些语法只有在 MyBatis 真正构建 SqlSessionFactory 时才会校验。
 * 本测试用 MyBatis 的 XMLMapperBuilder 逐个解析消息域 mapper，提前暴露启动期才会暴露的语法问题。</p>
 */
class MapperXmlParseTest {

	@Test
	void everyMessageMapperXmlParses() throws IOException {
		List<Path> mapperFiles;
		try (var paths = Files.list(Path.of("src/main/resources/mapper"))) {
			mapperFiles = paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
		}
		assertTrue(!mapperFiles.isEmpty(), "未找到消息域 mapper XML");
		for (Path mapperFile : mapperFiles) {
			Configuration configuration = new Configuration();
			try (InputStream inputStream = Files.newInputStream(mapperFile)) {
				new XMLMapperBuilder(inputStream, configuration, mapperFile.toString(),
						configuration.getSqlFragments()).parse();
			}
			catch (RuntimeException exception) {
				throw new AssertionError("Mapper XML 解析失败: " + mapperFile.getFileName(), exception);
			}
		}
	}

}
