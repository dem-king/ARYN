package com.aryn.cloud.common.myabtis.handler;

import com.aryn.cloud.common.myabtis.properties.TenantConfigProperties;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantLineHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.Data;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 租户表 UPDATE 的 SQL 级回归测试：验证自动填充与 MyBatis-Plus 租户插件的组合不会把
 * {@code tenant_id} 写成 NULL，也不会允许 UPDATE 改挂租户。
 *
 * <p>背景（2026-10-29 微信登录 500）：MyBatis-Plus 的 {@code updateById} 在生成动态 SQL
 * （包括 {@code <if test="et.tenantId != null">tenant_id = #{et.tenantId}</if>}）之后才执行
 * {@link MyMetaObjectHandler#updateFill}。旧实现在 updateFill 中把实体 tenantId 置空，此时
 * SET 子句已经包含 {@code tenant_id}，绑定时只能写入 NULL，命中 NOT NULL 约束：
 * {@code Column 'tenant_id' cannot be null}。本测试用真实 MyBatis-Plus 拦截器 + H2 复现该路径，
 * 不依赖 MySQL / Spring 容器。
 */
class TenantUpdateOwnershipSqlTest {

	private static final String TENANT_A = "159022980063364816";

	private static final String TENANT_B = "1881232176465358849";

	private SqlSession session;

	private ProbeMapper mapper;

	@BeforeEach
	void setUp() throws Exception {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:tenant-update-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE tenant_probe ("
					+ "id VARCHAR(32) PRIMARY KEY, name VARCHAR(64), tenant_id VARCHAR(32) NOT NULL, "
					+ "create_time TIMESTAMP, update_time TIMESTAMP, create_by VARCHAR(60), update_by VARCHAR(60))");
		}

		TenantConfigProperties tenantProperties = new TenantConfigProperties();
		tenantProperties.setTables(List.of("tenant_probe"));
		ArynTenantLineHandler tenantLineHandler = new ArynTenantLineHandler();
		ReflectionTestUtils.setField(tenantLineHandler, "tenantConfigProperties", tenantProperties);
		TenantLineInnerInterceptor tenantLineInterceptor = new TenantLineInnerInterceptor();
		tenantLineInterceptor.setTenantLineHandler(tenantLineHandler);
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(tenantLineInterceptor);

		MybatisConfiguration configuration = new MybatisConfiguration();
		configuration.setEnvironment(new Environment("tenant-update-test", new JdbcTransactionFactory(), dataSource));
		configuration.addInterceptor(interceptor);
		GlobalConfig globalConfig = new GlobalConfig();
		globalConfig.setDbConfig(new GlobalConfig.DbConfig());
		globalConfig.setMetaObjectHandler(new MyMetaObjectHandler());
		GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
		configuration.addMapper(ProbeMapper.class);

		SqlSessionFactory sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
		session = sqlSessionFactory.openSession(true);
		mapper = session.getMapper(ProbeMapper.class);

		ArynTenantContextHolder.setTenantId(TENANT_A);
		ProbeEntity probe = new ProbeEntity();
		probe.setId("row-1");
		probe.setName("before");
		probe.setTenantId(TENANT_A);
		assertThat(mapper.insert(probe)).isEqualTo(1);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
		if (session != null) {
			session.close();
		}
	}

	@Test
	void updateByIdKeepsTenantIdInsteadOfWritingNull() {
		ProbeEntity loaded = mapper.selectById("row-1");
		assertThat(loaded).isNotNull();
		loaded.setName("after");

		assertThatCode(() -> mapper.updateById(loaded)).as("租户表 UPDATE 不得写入 tenant_id = NULL").doesNotThrowAnyException();

		ProbeEntity reloaded = mapper.selectById("row-1");
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.getName()).isEqualTo("after");
		assertThat(reloaded.getTenantId()).isEqualTo(TENANT_A);
	}

	@Test
	void updateByIdCannotReassignTenantThroughEntityField() {
		ProbeEntity loaded = mapper.selectById("row-1");
		assertThat(loaded).isNotNull();
		loaded.setTenantId(TENANT_B);
		loaded.setName("moved");

		assertThatCode(() -> mapper.updateById(loaded)).doesNotThrowAnyException();

		ProbeEntity reloaded = mapper.selectById("row-1");
		assertThat(reloaded).isNotNull();
		assertThat(reloaded.getTenantId()).as("实体携带的其它租户值不得改挂记录归属").isEqualTo(TENANT_A);
	}

	@Data
	@TableName("tenant_probe")
	public static class ProbeEntity {

		@TableId(type = IdType.INPUT)
		private String id;

		private String name;

		private String tenantId;

		@TableField(fill = FieldFill.INSERT)
		private LocalDateTime createTime;

		@TableField(fill = FieldFill.UPDATE)
		private LocalDateTime updateTime;

		@TableField(fill = FieldFill.INSERT)
		private String createBy;

		@TableField(fill = FieldFill.UPDATE)
		private String updateBy;

	}

	public interface ProbeMapper extends BaseMapper<ProbeEntity> {

	}

}
