package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.*;
import com.aryn.cloud.product.mapper.*;
import com.aryn.cloud.product.support.CatalogIndexRepository;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** 仅允许显式指定的隔离数据库，使用真实MySQL、MyBatis租户插件和Spring事务。 */
@EnabledIfEnvironmentVariable(named = "ARYN_AI_TEST_JDBC_URL", matches = ".*")
class CatalogIndexPersistenceTest {
    private CatalogIndexRepository repository;
    private JdbcTemplate jdbc;
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    @BeforeEach void setup() throws Exception {
        String url = System.getenv("ARYN_AI_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"), "只允许本地隔离验证库");
        var dataSource = new DriverManagerDataSource(url, System.getenv("ARYN_AI_TEST_DB_USER"), System.getenv("ARYN_AI_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
        }));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(interceptor);
        var sessionFactory = factory.getObject();
        for (var mapper : List.of(CatalogVectorGenerationMapper.class, CatalogVectorItemMapper.class, CatalogVectorStateMapper.class))
            sessionFactory.getConfiguration().addMapper(mapper);
        var template = new SqlSessionTemplate(sessionFactory);
        var target = new CatalogIndexRepository(template.getMapper(CatalogVectorGenerationMapper.class),
            template.getMapper(CatalogVectorItemMapper.class), template.getMapper(CatalogVectorStateMapper.class));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        repository = (CatalogIndexRepository) proxy.getProxy();
        ArynTenantContextHolder.setTenantId(tenant);
    }
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }

    private CatalogVectorGeneration generation() {
        var g = new CatalogVectorGeneration();
        g.setId(UUID.randomUUID().toString()); g.setTenantId(tenant); g.setDelFlag("0");
        g.setConfigVersion("test-config"); g.setModelFingerprint("test-model"); g.setDimension(2);
        g.setStatus("QUEUED"); g.setIndexedRows(0); g.setExpectedRows(1);
        g.setCreateTime(LocalDateTime.now()); g.setUpdateTime(LocalDateTime.now());
        repository.insert(g); return g;
    }
    private CatalogVectorItem item(String generation, String sku) {
        var row = new CatalogVectorItem();
        row.setId(UUID.randomUUID().toString()); row.setGenerationId(generation); row.setSkuId(sku);
        row.setSourceName("隔离商品"); row.setVectorJson("[0.6,0.8]"); row.setTenantId(tenant); row.setDelFlag("0");
        row.setCreateTime(LocalDateTime.now()); row.setUpdateTime(LocalDateTime.now()); return row;
    }

    @Test void expiredLeaseFencesOldWorkerAndDuplicatePageRollsBackAtomically() {
        var g = generation(); String first = repository.claim(g.getId());
        assertNotNull(first); assertNull(repository.claim(g.getId()));
        jdbc.update("UPDATE catalog_vector_generation SET lease_until=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=? AND tenant_id=?", g.getId(), tenant);
        String second = repository.claim(g.getId()); assertNotNull(second); assertNotEquals(first, second);
        assertThrows(IllegalStateException.class, () -> repository.savePage(g.getId(), first, List.of(item(g.getId(), "1")), "1"));
        repository.savePage(g.getId(), second, List.of(item(g.getId(), "1")), "1");
        assertThrows(RuntimeException.class, () -> repository.savePage(g.getId(), second,
            List.of(item(g.getId(), "2"), item(g.getId(), "1")), "2"));
        assertEquals(1, repository.page(g.getId(), null).size());
        assertEquals("1", repository.get(g.getId()).getCursorId());
        repository.fail(g.getId(), first);
        assertEquals("BUILDING", repository.get(g.getId()).getStatus());
        repository.finish(g.getId(), second, "catalog", 1);
        repository.activate(tenant, g.getId(), 0);
        assertThrows(IllegalStateException.class, () -> repository.activate(tenant, g.getId(), 0));
        assertEquals(g.getId(), repository.active(tenant).getId());
        ArynTenantContextHolder.setTenantId("other-tenant");
        assertNull(repository.get(g.getId())); assertTrue(repository.page(g.getId(), null).isEmpty());
        assertNull(repository.claim(g.getId()));
    }

    @Test void competingWorkersClaimOnceAndIndexSwitchCanRollBack() throws Exception {
        var a = generation(); var b = generation();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> claim = () -> {
            ArynTenantContextHolder.setTenantId(tenant);
            try { start.await(); return repository.claim(a.getId()); }
            finally { ArynTenantContextHolder.removeTenantId(); }
        };
        try {
            Future<String> one = pool.submit(claim); Future<String> two = pool.submit(claim); start.countDown();
            String t1 = one.get(10, TimeUnit.SECONDS), t2 = two.get(10, TimeUnit.SECONDS);
            assertNotEquals(t1 == null, t2 == null);
            String token = t1 == null ? t2 : t1;
            repository.finish(a.getId(), token, "empty", 0);
            repository.finish(b.getId(), repository.claim(b.getId()), "empty", 0);
            repository.activate(tenant, a.getId(), 0);
            repository.activate(tenant, b.getId(), 1);
            repository.activate(tenant, a.getId(), 2);
            assertEquals(a.getId(), repository.active(tenant).getId());
            assertEquals(3, repository.state(tenant).getRevision());
        } finally { pool.shutdownNow(); }
    }
}
