package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.dto.B2bActivitySaveDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromoCalendarMapper;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService;
import com.aryn.cloud.promotion.service.impl.B2bActivityCommandService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实 MySQL、租户拦截器和事务代理；只允许 aryn_ai_verify_* 隔离库。 */
@EnabledIfEnvironmentVariable(named = "ARYN_AI_TEST_JDBC_URL", matches = ".*")
class B2bActivityPersistenceTest {

	private B2bActivityCommandService commands;
	private JdbcTemplate jdbc;
	private final String tenant = UUID.randomUUID().toString().substring(0, 20);

	@BeforeEach
	void setup() throws Exception {
		String url = System.getenv("ARYN_AI_TEST_JDBC_URL");
		assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"));
		var data = new DriverManagerDataSource(url, System.getenv("ARYN_AI_TEST_DB_USER"), System.getenv("ARYN_AI_TEST_DB_PASSWORD"));
		jdbc = new JdbcTemplate(data);
		var configuration = new MybatisConfiguration();
		configuration.setMapUnderscoreToCamelCase(true);
		var plugins = new MybatisPlusInterceptor();
		plugins.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
			public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
		}));
		var factory = new MybatisSqlSessionFactoryBean();
		factory.setDataSource(data);
		factory.setConfiguration(configuration);
		factory.setPlugins(plugins);
		var sessionFactory = factory.getObject();
		sessionFactory.getConfiguration().addMapper(PromotionActivityMapper.class);
		var mapper = new SqlSessionTemplate(sessionFactory).getMapper(PromotionActivityMapper.class);
		var target = new B2bActivityCommandService(mapper, new ActivityPublishGovernanceService(mapper), mock(PromoCalendarMapper.class));
		var proxy = new ProxyFactory(target);
		proxy.setProxyTargetClass(true);
		proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
		commands = (B2bActivityCommandService) proxy.getProxy();
		ArynTenantContextHolder.setTenantId(tenant);
	}

	@AfterEach
	void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@Test
	void updateClearsUnlimitedQuotaWithoutOverwritingConsumedAmountsAndCopyResetsThem() {
		var input = draft();
		input.setTotalQuota(10);
		var activity = commands.create(input);
		jdbc.update("UPDATE promotion_activity SET consumed_budget=12.5,consumed_quota=3 WHERE id=? AND tenant_id=?", activity.getId(), tenant);
		var edit = draft();
		edit.setExpectedRevision(0L);
		commands.update(activity.getId(), edit);
		var saved = commands.get(activity.getId());
		assertNull(saved.getTotalQuota());
		assertEquals(3, saved.getConsumedQuota());
		assertEquals(0, new BigDecimal("12.50").compareTo(saved.getConsumedBudget()));
		assertEquals(1L, saved.getRevision());
		var copy = commands.copy(activity.getId(), 1L);
		assertNotEquals(activity.getId(), copy.getId());
		assertEquals(0L, copy.getRevision());
		assertEquals(PromotionActivity.STATUS_DRAFT, copy.getStatus());
		assertEquals(0, copy.getConsumedQuota());
		commands.publish(activity.getId(), 1L, true);
		assertEquals(PromotionActivity.STATUS_PUBLISHED, commands.get(activity.getId()).getStatus());
	}

	@Test
	void sameRevisionConcurrentPublishesHaveOneWinner() throws Exception {
		var activity = commands.create(draft());
		var pool = Executors.newFixedThreadPool(2);
		var latch = new CountDownLatch(1);
		Callable<Boolean> publish = () -> {
			ArynTenantContextHolder.setTenantId(tenant);
			try { latch.await(); commands.publish(activity.getId(), 0L, true); return true; }
			catch (RuntimeException conflict) { return false; }
			finally { ArynTenantContextHolder.removeTenantId(); }
		};
		try {
			var one = pool.submit(publish);
			var two = pool.submit(publish);
			latch.countDown();
			assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
			assertEquals(1L, commands.get(activity.getId()).getRevision());
		}
		finally { pool.shutdownNow(); }
	}

	@Test
	void tenantIsolationAndLogicalDeleteApplyToAllCommandReads() {
		var activity = commands.create(draft());
		ArynTenantContextHolder.setTenantId("another-tenant");
		assertThrows(RuntimeException.class, () -> commands.get(activity.getId()));
		assertThrows(RuntimeException.class, () -> commands.publish(activity.getId(), 0L, true));
		ArynTenantContextHolder.setTenantId(tenant);
		commands.delete(activity.getId(), 0L);
		assertThrows(RuntimeException.class, () -> commands.get(activity.getId()));
		assertEquals("1", jdbc.queryForObject("SELECT del_flag FROM promotion_activity WHERE id=? AND tenant_id=?", String.class, activity.getId(), tenant));
	}

	private B2bActivitySaveDTO draft() {
		var dto = new B2bActivitySaveDTO();
		dto.setActivityName("隔离测试满额优惠");
		dto.setActivityType("7");
		dto.setScopeType("1");
		dto.setStartTime(LocalDateTime.now().minusDays(1));
		dto.setEndTime(LocalDateTime.now().plusDays(1));
		dto.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}");
		return dto;
	}
}
