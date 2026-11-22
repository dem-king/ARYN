package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.ProcurementCommands.*;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.aryn.cloud.order.service.impl.ProcurementDraftService;
import com.aryn.cloud.order.support.procurement.*;
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
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实隔离MySQL、租户插件和事务代理；不启动完整应用或触碰存量业务库。 */
@EnabledIfEnvironmentVariable(named = "ARYN_AI_TEST_JDBC_URL", matches = ".*")
class ProcurementPersistenceTest {
    private ProcurementRepository repository;
    private ProcurementDraftService service;
    private ProcurementDraftRowMapper rows;
    private JdbcTemplate jdbc;
    private ProcurementConfirmationRepository confirmations;
    private ProcurementContributionLedger ledger;
    private SharedCartMapper cartMapper;
    private SharedCartItemMapper itemMapper;
    private SharedCartMemberMapper memberMapper;
    private com.aryn.cloud.order.service.impl.ProcurementReviewService reviews;
    private org.springframework.transaction.support.TransactionTemplate transactions;
    private com.aryn.cloud.order.service.impl.ProcurementJobService jobs;
    private com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService products;
    private com.aryn.cloud.order.service.impl.ProcurementProcessor processor;
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    @BeforeEach void setup() throws Exception {
        String url = System.getenv("ARYN_AI_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"));
        var dataSource = new DriverManagerDataSource(url, System.getenv("ARYN_AI_TEST_DB_USER"), System.getenv("ARYN_AI_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
        }));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(dataSource);
        factory.setConfiguration(configuration); factory.setPlugins(interceptor);
        var sessionFactory = factory.getObject();
        for (var mapper : List.of(ProcurementDraftMapper.class, ProcurementSourceBatchMapper.class,
                ProcurementDraftRowMapper.class, ProcurementContributionMapper.class, SharedCartMapper.class, SharedCartItemMapper.class, SharedCartMemberMapper.class)) sessionFactory.getConfiguration().addMapper(mapper);
        var template = new SqlSessionTemplate(sessionFactory);
        rows = template.getMapper(ProcurementDraftRowMapper.class);
        var target = new ProcurementRepository(template.getMapper(ProcurementDraftMapper.class),
            template.getMapper(ProcurementSourceBatchMapper.class), rows, template.getMapper(ProcurementContributionMapper.class));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        repository = (ProcurementRepository) proxy.getProxy();
        var jobTarget = new com.aryn.cloud.order.service.impl.ProcurementJobService(template.getMapper(ProcurementSourceBatchMapper.class), rows);
        var jobProxy = new ProxyFactory(jobTarget); jobProxy.setProxyTargetClass(true);
        jobProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        jobs = (com.aryn.cloud.order.service.impl.ProcurementJobService) jobProxy.getProxy();
        var access = new ProcurementAccess(mock(IBuyerOrgService.class));
        var runtime = mock(com.aryn.cloud.order.support.OrderAiRuntime.class);
        service = new ProcurementDraftService(repository, access, runtime, mock(org.springframework.context.ApplicationEventPublisher.class), jobs);
        products = mock(com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService.class);
        var matching = new com.aryn.cloud.order.service.impl.ProcurementMatchingService(new com.fasterxml.jackson.databind.ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(matching, "products", products);
        processor = new com.aryn.cloud.order.service.impl.ProcurementProcessor(repository, access, jobs,
            new com.aryn.cloud.order.service.impl.ProcurementExtractionService(2000), matching, runtime);
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        cartMapper = template.getMapper(SharedCartMapper.class); itemMapper = template.getMapper(SharedCartItemMapper.class); memberMapper = template.getMapper(SharedCartMemberMapper.class);
        var ledgerTarget = new ProcurementContributionLedger(template.getMapper(ProcurementContributionMapper.class));
        var ledgerProxy = new ProxyFactory(ledgerTarget); ledgerProxy.setProxyTargetClass(true);
        ledgerProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        ledger = (ProcurementContributionLedger) ledgerProxy.getProxy();
        var confirmTarget = new ProcurementConfirmationRepository(repository, template.getMapper(ProcurementSourceBatchMapper.class),
            template.getMapper(ProcurementDraftMapper.class), rows, template.getMapper(ProcurementContributionMapper.class), ledger,
            new ProcurementCartTarget(cartMapper, itemMapper, memberMapper), objectMapper);
        var confirmProxy = new ProxyFactory(confirmTarget); confirmProxy.setProxyTargetClass(true);
        confirmProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        confirmations = (ProcurementConfirmationRepository) confirmProxy.getProxy();
        transactions = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var reviewTarget = new ProcurementReviewRepository(template.getMapper(ProcurementSourceBatchMapper.class), rows, objectMapper);
        var reviewProxy = new ProxyFactory(reviewTarget); reviewProxy.setProxyTargetClass(true);
        reviewProxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        reviews = new com.aryn.cloud.order.service.impl.ProcurementReviewService(service, (ProcurementReviewRepository) reviewProxy.getProxy(), objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(reviews, "products", products);
        ArynTenantContextHolder.setTenantId(tenant);
    }
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }
    private ProcurementDraft create(String actor, String request) {
        return service.create(tenant, actor, new CreateDraft(request, "1", null, null, null, "采购"));
    }
    @Test void requestReplayContentDuplicateAndExplicitNewHaveDifferentSemanticsAcrossDays() {
        var draft = create("actor", "draft-request");
        assertEquals(draft.getId(), create("actor", "draft-request").getId());
        assertThrows(RuntimeException.class, () -> service.create(tenant, "actor",
            new CreateDraft("draft-request", "1", null, null, null, "不同采购")));
        var command = new SubmitText("batch-request", 0, "苹果3斤", false);
        var first = service.submitText(tenant, "actor", draft.getId(), command);
        assertEquals("CREATED", first.decision());
        jdbc.update("UPDATE procurement_draft SET create_time=DATE_SUB(NOW(),INTERVAL 3 DAY),update_time=DATE_SUB(NOW(),INTERVAL 3 DAY) WHERE id=? AND tenant_id=?", draft.getId(), tenant);
        assertEquals("OPEN", service.get(tenant, "actor", draft.getId()).getStatus());
        assertEquals(first.batchId(), service.submitText(tenant, "actor", draft.getId(), command).batchId());
        assertThrows(RuntimeException.class, () -> service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request", 0, "香蕉", false)));
        var duplicate = service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request2", 1, "苹果3斤", false));
        assertEquals("DUPLICATE", duplicate.decision()); assertEquals(first.batchId(), duplicate.batchId());
        var fresh = service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request3", 1, "苹果3斤", true));
        assertNotEquals(first.batchId(), fresh.batchId()); assertEquals(2L, fresh.draftRevision());
        assertEquals(2, repository.batches(draft.getId()).size());
        var another = create("other-actor", "draft-request");
        assertEquals("CREATED", service.submitText(tenant, "other-actor", another.getId(), command).decision());
        assertThrows(RuntimeException.class, () -> service.get(tenant, "other-actor", draft.getId()));
        ArynTenantContextHolder.setTenantId("another-tenant");
        assertNull(repository.draft(draft.getId(), false)); assertNull(repository.batch(first.batchId()));
    }
    @Test void racingNetworkRetriesCreateOneBatchAndKeepDecimalEvidenceAfterReload() throws Exception {
        var draft = create("actor", "draft-request");
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<BatchSubmission> submit = () -> {
            ArynTenantContextHolder.setTenantId(tenant);
            try { start.await(); return service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request", 0, "油0.123456789升", false)); }
            finally { ArynTenantContextHolder.removeTenantId(); }
        };
        try {
            var one = pool.submit(submit); var two = pool.submit(submit); start.countDown();
            var a = one.get(10, TimeUnit.SECONDS); var b = two.get(10, TimeUnit.SECONDS);
            assertEquals(a.batchId(), b.batchId()); assertEquals(1, repository.batches(draft.getId()).size());
            var row = new ProcurementDraftRow(); row.setId(UUID.randomUUID().toString()); row.setDraftId(draft.getId());
            row.setBatchId(a.batchId()); row.setSourcePosition("00000001:0001"); row.setRawText("油0.123456789升");
            row.setRawQuantity(new BigDecimal("0.123456789")); row.setRawUnit("升"); row.setParseRevision(1);
            row.setTenantId(tenant); row.setDelFlag("0"); row.setRevision(0L); rows.insert(row);
            var reloaded = repository.rows(a.batchId(), null).get(0);
            assertEquals(0, new BigDecimal("0.123456789").compareTo(reloaded.getRawQuantity()));
            assertEquals("升", reloaded.getRawUnit());
            assertEquals("油0.123456789升", service.batch(tenant, "actor", draft.getId(), a.batchId()).getInputText());
        } finally { pool.shutdownNow(); }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<com.aryn.cloud.product.api.vo.ReplenishImportMatchVO> productResults(org.mockito.invocation.InvocationOnMock call) {
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(), "外部RPC时不得占用事务");
        var inputs = (java.util.List<com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO>) call.getArgument(1);
        return inputs.stream().map(input -> {
            var fact = new com.aryn.cloud.product.api.vo.ReplenishImportMatchVO(); fact.setRowNo(input.getRowNo());
            fact.setMatchType("CODE"); fact.setSkuId("sku"); fact.setName("苹果"); fact.setSkuStatus("0"); fact.setSpuStatus("1"); fact.setStock(10000);
            fact.setMeasurement(new com.aryn.cloud.product.api.support.ProductMeasurementRules.Measurement("件", null, null, null, "UNKNOWN", 1, 1, 0L));
            return fact;
        }).toList();
    }
    @Test void expiredLeaseAndCancellationFenceLateWorkersWithoutChangingSavedEvidence() {
        var draft = create("actor", "draft-request");
        var input = service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request", 0, "苹果2件", false));
        String old = jobs.claim(input.batchId()); assertNotNull(old); assertNull(jobs.claim(input.batchId()));
        jdbc.update("UPDATE procurement_source_batch SET lease_until=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=? AND tenant_id=?", input.batchId(), tenant);
        String current = jobs.claim(input.batchId()); assertNotEquals(old, current);
        assertThrows(RuntimeException.class, () -> jobs.heartbeat(input.batchId(), old));
        jobs.fail(input.batchId(), old, "OLD_WORKER"); assertEquals("RUNNING", repository.batch(input.batchId()).getStatus());
        var batch = repository.batch(input.batchId()); jobs.cancel(input.batchId(), batch.getRevision());
        assertThrows(RuntimeException.class, () -> jobs.saveChunk(input.batchId(), current, List.of(), 0, 1));
        jobs.fail(input.batchId(), current, "LATE_RESULT"); assertEquals("CANCELLED", repository.batch(input.batchId()).getStatus());
    }
    @Test void restartedProcessorResumesAfterSuccessfulChunkAndDoesNotRepeatSavedRows() {
        var draft = create("actor", "draft-request");
        var input = service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request", 0, "苹果2件\n".repeat(65), false));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(products.matchRows(eq(tenant), anyList())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("temporary RPC failure");
            return productResults(call);
        });
        processor.process(input.batchId());
        var failed = repository.batch(input.batchId()); assertEquals("FAILED", failed.getStatus());
        assertEquals(32, failed.getCompletedRows()); assertEquals(32, repository.rows(input.batchId(), null).size());
        jobs.retry(input.batchId(), failed.getRevision());
        when(products.matchRows(eq(tenant), anyList())).thenAnswer(this::productResults);
        processor.process(input.batchId());
        var complete = repository.batch(input.batchId()); assertTrue(List.of("READY", "REVIEW_REQUIRED").contains(complete.getStatus()));
        assertEquals(65, complete.getCompletedRows()); assertEquals(65, repository.rows(input.batchId(), null).size());
        assertEquals(0, new BigDecimal("2").compareTo(repository.rows(input.batchId(), null).get(0).getRawQuantity()));
    }
    @Test void cancellingWhileExternalRpcRunsRejectsItsLateResult() throws Exception {
        var draft = create("actor", "draft-request");
        var input = service.submitText(tenant, "actor", draft.getId(), new SubmitText("batch-request", 0, "苹果2件", false));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(products.matchRows(eq(tenant), anyList())).thenAnswer(call -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return productResults(call); });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var work = pool.submit(() -> { ArynTenantContextHolder.setTenantId(tenant); try { processor.process(input.batchId()); } finally { ArynTenantContextHolder.removeTenantId(); } });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var running = repository.batch(input.batchId()); service.cancel(tenant, "actor", draft.getId(), input.batchId(), running.getRevision());
            release.countDown(); work.get(10, TimeUnit.SECONDS);
            assertEquals("CANCELLED", repository.batch(input.batchId()).getStatus()); assertTrue(repository.rows(input.batchId(), null).isEmpty());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    private ProcurementSourceBatch ready(ProcurementDraft draft, String request, String text) {
        var submission = service.submitText(tenant, "actor", draft.getId(), new SubmitText(request,
            repository.draft(draft.getId(), false).getRevision(), text, true));
        when(products.matchRows(eq(tenant), anyList())).thenAnswer(this::productResults);
        processor.process(submission.batchId()); return repository.batch(submission.batchId());
    }
    private Map<String, com.aryn.cloud.product.api.vo.ReplenishImportMatchVO> facts() {
        var fact = new com.aryn.cloud.product.api.vo.ReplenishImportMatchVO(); fact.setSkuId("sku"); fact.setSpuId("spu");
        fact.setSkuStatus("0"); fact.setSpuStatus("1"); fact.setStock(10000);
        fact.setMeasurement(new com.aryn.cloud.product.api.support.ProductMeasurementRules.Measurement("件", null, null, null, "UNKNOWN", 1, 1, 0L));
        return Map.of("sku", fact);
    }
    private com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation confirm(ProcurementDraft draft,
            ProcurementSourceBatch batch, String mode, ProcurementSourceBatch old, String type, String target) {
        var versions = confirmations.allRows(batch.getId()).stream().collect(java.util.stream.Collectors.toMap(ProcurementDraftRow::getId, ProcurementDraftRow::getRevision));
        return confirmations.confirm("actor", draft.getId(), batch.getId(), new com.aryn.cloud.order.api.dto.ProcurementConfirmCommand(
            "confirm-request-" + UUID.randomUUID(), batch.getRevision(), mode, old == null ? null : old.getId(), old == null ? null : old.getRevision(), type, target),
            facts(), versions, java.time.Instant.now());
    }
    @Test void confirmedBatchReplaysWithAnotherRequestAndReplaceOnlyRevokesItsContribution() {
        var draft = create("actor", "draft-request");
        var a = ready(draft, "batch-request-a", "苹果2件");
        var first = confirm(draft, a, "ADD", null, "DIRECT", null); assertEquals(2, first.items().get(0).quantity());
        var again = confirm(draft, a, "ADD", null, "DIRECT", null); assertEquals(first, again);
        var b = ready(draft, "batch-request-b", "苹果3件");
        assertEquals(5, confirm(draft, b, "ADD", null, "DIRECT", null).items().get(0).quantity());
        var replacement = ready(draft, "batch-request-c", "苹果4件");
        var result = confirm(draft, replacement, "REPLACE", repository.batch(a.getId()), "DIRECT", null);
        assertEquals(7, result.items().get(0).quantity()); assertEquals("SUPERSEDED", repository.batch(a.getId()).getStatus());
        assertTrue(confirmations.batchContributions(a.getId()).stream().allMatch(value -> "REVOKED".equals(value.getStatus())));
        assertEquals(3, confirmations.batchContributions(b.getId()).get(0).getQuantity());
    }
    @Test void sharedReplacementPreservesOtherMemberLegacyAndLaterManualIncrementButRejectsOverwrite() {
        var draft = create("actor", "draft-request");
        jdbc.update("UPDATE procurement_draft SET purchase_scene='3',buyer_org_id='org' WHERE id=? AND tenant_id=?", draft.getId(), tenant);
        draft = repository.draft(draft.getId(), false);
        var cart = new SharedCart(); cart.setId(UUID.randomUUID().toString()); cart.setTenantId(tenant); cart.setCartType("2");
        cart.setBuyerOrgId("org"); cart.setOwnerUserId("actor"); cart.setStatus("2"); cart.setDelFlag("0"); cartMapper.insert(cart);
        var member = new SharedCartMember(); member.setId(UUID.randomUUID().toString()); member.setTenantId(tenant); member.setCartId(cart.getId());
        member.setUserId("actor"); member.setDisplayName("张三"); member.setCanEdit("1"); member.setCanConfirm("1"); member.setDelFlag("0"); memberMapper.insert(member);
        var other = new SharedCartItem(); other.setId(UUID.randomUUID().toString()); other.setTenantId(tenant); other.setCartId(cart.getId());
        other.setUserId("another-member"); other.setAttributedName(""); other.setSkuId("sku"); other.setSpuId("spu");
        other.setRequestedQuantity(6); other.setStatus("1"); other.setDelFlag("0"); itemMapper.insert(other);
        var legacy = new SharedCartItem(); legacy.setId(UUID.randomUUID().toString()); legacy.setTenantId(tenant); legacy.setCartId(cart.getId());
        legacy.setUserId("actor"); legacy.setAttributedName(""); legacy.setSkuId("sku"); legacy.setSpuId("spu");
        legacy.setRequestedQuantity(1); legacy.setStatus("1"); legacy.setDelFlag("0"); itemMapper.insert(legacy);
        var a = ready(draft, "batch-request-a", "苹果2件"); confirm(draft, a, "ADD", null, "SHARED_CART", cart.getId());
        var own = itemMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SharedCartItem>lambdaQuery()
            .eq(SharedCartItem::getTenantId, tenant).eq(SharedCartItem::getCartId, cart.getId()).eq(SharedCartItem::getUserId, "actor"));
        transactions.execute(status -> { ledger.manualShared(cart.getId(), own.getId(), "sku", "actor", 3, 6, true); own.setRequestedQuantity(6); itemMapper.updateById(own); return null; });
        var replacement = ready(draft, "batch-request-b", "苹果4件");
        confirm(draft, replacement, "REPLACE", repository.batch(a.getId()), "SHARED_CART", cart.getId());
        assertEquals(8, itemMapper.selectById(own.getId()).getRequestedQuantity()); assertEquals(6, itemMapper.selectById(other.getId()).getRequestedQuantity());
        transactions.execute(status -> { ledger.manualShared(cart.getId(), own.getId(), "sku", "actor", 8, 9, false); own.setRequestedQuantity(9); itemMapper.updateById(own); return null; });
        var later = ready(draft, "batch-request-c", "苹果8件");
        var finalDraft = draft;
        assertThrows(RuntimeException.class, () -> confirm(finalDraft, later, "REPLACE", repository.batch(replacement.getId()), "SHARED_CART", cart.getId()));
        assertEquals(9, itemMapper.selectById(own.getId()).getRequestedQuantity()); assertEquals("READY", repository.batch(later.getId()).getStatus());
    }
    @Test void parallelConfirmationOfSameBatchReturnsOneReceiptAndOneContribution() throws Exception {
        var draft = create("actor", "draft-request"); var batch = ready(draft, "batch-request", "苹果2件");
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<Future<com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                ArynTenantContextHolder.setTenantId(tenant);
                try { start.await(); return confirm(draft, batch, "ADD", null, "DIRECT", null); }
                finally { ArynTenantContextHolder.removeTenantId(); }
            }));
            start.countDown();
            assertEquals(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, confirmations.batchContributions(batch.getId()).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void checkoutRestoresCurrentContributionsAndRejectsChangedMeasurement() {
        var draft = create("actor", "draft-request"); var batch = ready(draft, "batch-request", "苹果2件");
        confirm(draft, batch, "ADD", null, "DIRECT", null);
        var checkout = new com.aryn.cloud.order.service.impl.ProcurementCheckoutService(service, confirmations);
        org.springframework.test.util.ReflectionTestUtils.setField(checkout, "products", products);
        when(products.matchSkuIds(eq(tenant), anyList())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return new ArrayList<>(facts().values());
        });
        assertEquals(2, checkout.current(tenant, "actor", draft.getId()).items().get(0).quantity());
        var changed = facts().get("sku");
        changed.setMeasurement(new com.aryn.cloud.product.api.support.ProductMeasurementRules.Measurement("箱", null, null, null, "UNKNOWN", 1, 1, 1L));
        when(products.matchSkuIds(eq(tenant), anyList())).thenReturn(List.of(changed));
        assertThrows(RuntimeException.class, () -> checkout.current(tenant, "actor", draft.getId()));
    }
    @Test void historicalRowsKeepMembersAndUnknownUnitsUntilExplicitSalesReview() {
        var draft = create("actor", "draft-request");
        var orderMapper = mock(OrderInfoMapper.class); var orderItems = mock(OrderItemMapper.class);
        var original = new OrderInfo(); original.setId("original-order"); original.setUserId("actor"); original.setPurchaseScene("1");
        when(orderMapper.selectOne(any())).thenReturn(original);
        var a = new OrderItemEntity(); a.setSkuId("sku"); a.setSpuName("苹果"); a.setBuyQuantity(2); a.setContributorName("张三");
        var b = new OrderItemEntity(); b.setSkuId("sku"); b.setSpuName("苹果"); b.setBuyQuantity(3); b.setContributorName("李四");
        when(orderItems.selectList(any())).thenReturn(List.of(a, b));
        var history = new com.aryn.cloud.order.service.impl.ProcurementHistoryService(service, mock(com.aryn.cloud.order.service.IOrderInfoService.class),
            mock(com.aryn.cloud.order.service.ISharedCartService.class), orderMapper, orderItems, itemMapper, memberMapper, new com.fasterxml.jackson.databind.ObjectMapper());
        var command = new com.aryn.cloud.order.service.impl.ProcurementHistoryService.HistoryCommand("history-request", draft.getRevision(), "REORDER", "original-order", null, null, false);
        var submission = history.submit(tenant, "actor", draft.getId(), command);
        assertEquals(submission.batchId(), history.submit(tenant, "actor", draft.getId(), command).batchId());
        when(products.matchSkuIds(eq(tenant), anyList())).thenReturn(new ArrayList<>(facts().values()));
        processor.process(submission.batchId());
        var saved = repository.rows(submission.batchId(), null);
        assertEquals(List.of("张三", "李四"), saved.stream().map(ProcurementDraftRow::getRawOwner).toList());
        assertTrue(saved.stream().allMatch(row -> row.getRawUnit() == null && "REQUIRED".equals(row.getReviewStatus())));
        for (var row : saved) reviews.review(tenant, "actor", draft.getId(), submission.batchId(), row.getId(),
            new com.aryn.cloud.order.api.dto.ProcurementReviewCommand(row.getRevision(), "CONFIRM_SALES", "sku", row.getRawQuantity().intValueExact(), false, "按当前销售单位重新确认"));
        assertEquals(5, confirm(draft, repository.batch(submission.batchId()), "ADD", null, "DIRECT", null).items().get(0).quantity());
    }
    @Test void separateDraftsConfirmingSameCartSerializeWithoutStaleContributionSnapshots() throws Exception {
        var a = service.create(tenant, "actor", new CreateDraft("draft-request-a", "3", "org", null, null, null));
        var b = service.create(tenant, "actor", new CreateDraft("draft-request-b", "3", "org", null, null, null));
        var first = ready(a, "batch-request-a", "苹果2件"); var second = ready(b, "batch-request-b", "苹果3件");
        var cart = new SharedCart(); cart.setId(UUID.randomUUID().toString()); cart.setTenantId(tenant); cart.setCartType("2");
        cart.setBuyerOrgId("org"); cart.setOwnerUserId("actor"); cart.setStatus("2"); cart.setDelFlag("0"); cartMapper.insert(cart);
        var member = new SharedCartMember(); member.setId(UUID.randomUUID().toString()); member.setTenantId(tenant); member.setCartId(cart.getId());
        member.setUserId("actor"); member.setDisplayName("张三"); member.setCanEdit("1"); member.setCanConfirm("1"); member.setDelFlag("0"); memberMapper.insert(member);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var tasks = new ArrayList<Future<com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation>>();
            for (int index = 0; index < 2; index++) {
                final int position = index;
                tasks.add(pool.submit(() -> {
                    ArynTenantContextHolder.setTenantId(tenant);
                    try { start.await(); return confirm(position == 0 ? a : b, position == 0 ? first : second, "ADD", null, "SHARED_CART", cart.getId()); }
                    finally { ArynTenantContextHolder.removeTenantId(); }
                }));
            }
            start.countDown(); for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            var target = itemMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SharedCartItem>lambdaQuery()
                .eq(SharedCartItem::getTenantId, tenant).eq(SharedCartItem::getCartId, cart.getId()));
            assertEquals(5, target.getRequestedQuantity()); assertEquals(5, ledger.sum(ledger.active("SHARED_CART", target.getId())));
            assertEquals(5, Math.max(tasks.get(0).get().items().get(0).quantity(), tasks.get(1).get().items().get(0).quantity()));
        } finally { pool.shutdownNow(); }
    }
    @Test void reviewRevisionConflictReturnsCurrentRowAndFactsChangeRejectsConfirmationAtomically() {
        var draft = create("actor", "draft-request"); var batch = ready(draft, "batch-request", "苹果2件");
        var row = repository.rows(batch.getId(), null).get(0);
        when(products.matchSkuIds(eq(tenant), anyList())).thenReturn(new ArrayList<>(facts().values()));
        var command = new com.aryn.cloud.order.api.dto.ProcurementReviewCommand(0, "CONFIRM_SALES", "sku", 3, false, "实际采购3件");
        var applied = reviews.review(tenant, "actor", draft.getId(), batch.getId(), row.getId(), command);
        assertEquals("APPLIED", applied.decision()); assertEquals(3, applied.row().getConfirmedSalesQuantity());
        var conflict = reviews.review(tenant, "actor", draft.getId(), batch.getId(), row.getId(), command);
        assertEquals("CONFLICT", conflict.decision()); assertEquals(1, conflict.row().getRevision());
        assertTrue(conflict.row().getDecisionJson().contains("实际采购3件"));
        var fact = facts().get("sku"); fact.setMeasurement(new com.aryn.cloud.product.api.support.ProductMeasurementRules.Measurement("件", null, null, null, "UNKNOWN", 1, 1, 4L));
        var current = repository.batch(batch.getId());
        assertThrows(RuntimeException.class, () -> confirmations.confirm("actor", draft.getId(), batch.getId(), new com.aryn.cloud.order.api.dto.ProcurementConfirmCommand(
            "confirm-request", current.getRevision(), "ADD", null, null, "DIRECT", null), Map.of("sku", fact), Map.of(row.getId(), 1L), java.time.Instant.now()));
        assertTrue(confirmations.batchContributions(batch.getId()).isEmpty()); assertEquals("READY", repository.batch(batch.getId()).getStatus());
    }
}
