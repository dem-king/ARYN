package com.aryn.cloud.boot.ai;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.DeliveryGroupCommands.*;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 新三表用真实增量DDL；旧订单/仓库为隔离fixture，外域靠港为RPC fixture。 */
@EnabledIfEnvironmentVariable(named = "ARYN_AI_TEST_JDBC_URL", matches = ".*")
class DeliveryGroupPersistenceTest {
    private DeliveryGroupService service;
    private OrderInfoMapper orders;
    private DeliveryGroupMapper groups;
    private DeliveryGroupOperationMapper operations;
    private com.aryn.cloud.order.service.impl.FulfillmentServiceImpl fulfillment;
    private com.aryn.cloud.order.service.impl.DeliveryGroupOutboxService outbox;
    private org.apache.rocketmq.spring.core.RocketMQTemplate mq;
    private JdbcTemplate jdbc;
    private RemoteVesselService vessels;
    private VesselContextDTO call;
    private String warehouse;
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);
    private final LocalDateTime start = LocalDateTime.now().plusHours(3).withNano(0);
    @BeforeEach void setup() throws Exception {
        String url = System.getenv("ARYN_AI_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aryn_ai_verify_[a-zA-Z0-9_]+(?:\\?.*)?"));
        var data = new DriverManagerDataSource(url, System.getenv("ARYN_AI_TEST_DB_USER"), System.getenv("ARYN_AI_TEST_DB_PASSWORD")); jdbc = new JdbcTemplate(data);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        var interceptor = new MybatisPlusInterceptor(); interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            public Expression getTenantId() { return new StringValue(ArynTenantContextHolder.getTenantId()); }
        }));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(data); factory.setConfiguration(config); factory.setPlugins(interceptor);
        var sf = factory.getObject();
        for (var mapper : List.of(DeliveryGroupMapper.class, DeliveryGroupOrderLinkMapper.class, DeliveryGroupOperationMapper.class,
                DeliveryWarehouseConfigMapper.class, OrderInfoMapper.class, DeliveryTaskMapper.class, FulfillmentWaveMapper.class,
                FulfillmentPickItemMapper.class, FulfillmentExceptionMapper.class, OrderItemMapper.class, SharedCartMapper.class)) sf.getConfiguration().addMapper(mapper);
        var session = new SqlSessionTemplate(sf);
        groups = session.getMapper(DeliveryGroupMapper.class); operations = session.getMapper(DeliveryGroupOperationMapper.class); orders = session.getMapper(OrderInfoMapper.class);
        var warehouses = session.getMapper(DeliveryWarehouseConfigMapper.class);
        service = new DeliveryGroupService(groups, session.getMapper(DeliveryGroupOrderLinkMapper.class), operations, warehouses,
            orders, session.getMapper(DeliveryTaskMapper.class), session.getMapper(FulfillmentWaveMapper.class), session.getMapper(FulfillmentPickItemMapper.class), new ObjectMapper().findAndRegisterModules(), new TransactionTemplate(new DataSourceTransactionManager(data)));
        var bridge = new com.aryn.cloud.order.support.DeliveryGroupFulfillment(groups, session.getMapper(DeliveryGroupOrderLinkMapper.class), warehouses, session.getMapper(DeliveryTaskMapper.class), service);
        var target = new com.aryn.cloud.order.service.impl.FulfillmentServiceImpl(session.getMapper(FulfillmentWaveMapper.class), session.getMapper(FulfillmentPickItemMapper.class),
            session.getMapper(FulfillmentExceptionMapper.class), orders, session.getMapper(OrderItemMapper.class), session.getMapper(SharedCartMapper.class), bridge);
        var proxy = new org.springframework.aop.framework.ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new DataSourceTransactionManager(data), new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        fulfillment = (com.aryn.cloud.order.service.impl.FulfillmentServiceImpl) proxy.getProxy();
        mq = mock(org.apache.rocketmq.spring.core.RocketMQTemplate.class);
        outbox = new com.aryn.cloud.order.service.impl.DeliveryGroupOutboxService(operations, new ObjectMapper().findAndRegisterModules(), mq);
        vessels = mock(RemoteVesselService.class); ReflectionTestUtils.setField(service, "vessels", vessels);
        ArynTenantContextHolder.setTenantId(tenant);
        warehouse = UUID.randomUUID().toString(); var row = new DeliveryWarehouseConfig(); row.setId(warehouse); row.setTenantId(tenant); row.setDelFlag("0"); warehouses.insert(row);
        call = new VesselContextDTO(); call.setVesselId("ship"); call.setVesselCallId("call"); call.setPortCode("port"); call.setCallOrderable(true);
        call.setDeliveryWindowStart(start.minusHours(2)); call.setDeliveryWindowEnd(start.plusHours(5));
        when(vessels.getVesselCallContext(tenant, "call")).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()); return call;
        });
    }
    @AfterEach void cleanup() { ArynTenantContextHolder.removeTenantId(); }
    private Receipt create(int sequence) {
        return service.create(tenant, "admin", new Create("create-request-" + sequence, warehouse, "call", sequence, start, start.plusHours(1), start.minusHours(1), 20, "已核定本窗口运力"));
    }
    private OrderInfo order(String actor) {
        var row = new OrderInfo(); row.setId(UUID.randomUUID().toString()); row.setTenantId(tenant); row.setUserId(actor);
        row.setVesselId("ship"); row.setVesselCallId("call"); row.setPortCode("port"); row.setStatus("2"); row.setDeliveryWay("4");
        row.setDeliveryWindowStart(start); row.setDeliveryWindowEnd(start.plusHours(1)); row.setPurchaseScene("1"); row.setDelFlag("0"); orders.insert(row); return row;
    }
    private Change command(String request, long revision, String action, String... ids) {
        return new Change(request, revision, action, List.of(ids), null, null, null, null, false, "人工核对");
    }
    @Test void independentOrdersReplayAndBuyerVisibilityRemainSeparate() {
        var group = create(1); assertEquals(group, create(1)); var a = order("buyer-a"); var b = order("buyer-b");
        var input = command("join-request", 0, "JOIN", a.getId(), b.getId());
        var receipt = service.change(tenant, "admin", group.groupId(), input);
        assertEquals(receipt, service.change(tenant, "admin", group.groupId(), input));
        assertEquals(2, service.detail(tenant, "admin", group.groupId()).orders().size());
        var own = service.buyerSchedule(tenant, "buyer-a", a.getId()); assertEquals(group.groupId(), own.groupId());
        assertThrows(RuntimeException.class, () -> service.buyerSchedule(tenant, "buyer-a", b.getId()));
        assertEquals("2", orders.selectById(a.getId()).getStatus()); assertEquals("2", orders.selectById(b.getId()).getStatus());
        var notices = service.detail(tenant, "admin", group.groupId()).operations().stream().filter(op -> "JOIN".equals(op.getAction())).findFirst().orElseThrow();
        assertEquals("PENDING", notices.getOutboxStatus()); assertTrue(notices.getNotificationJson().contains(a.getId()));
        assertFalse(notices.getNotificationJson().contains("recipientPhone"));
        ArynTenantContextHolder.setTenantId("another"); assertThrows(RuntimeException.class, () -> service.detail("another", "admin", group.groupId()));
    }
    @Test void sameRequestRacingUsesOneReceiptAndNeverAddsTwice() throws Exception {
        var group = create(1); var order = order("buyer"); var command = command("racing-request", 0, "JOIN", order.getId());
        var pool = Executors.newFixedThreadPool(2); var latch = new CountDownLatch(1);
        Callable<Receipt> work = () -> { ArynTenantContextHolder.setTenantId(tenant); try { latch.await(); return service.change(tenant, "admin", group.groupId(), command); } finally { ArynTenantContextHolder.removeTenantId(); } };
        try {
            var a = pool.submit(work); var b = pool.submit(work); latch.countDown(); assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, service.detail(tenant, "admin", group.groupId()).orders().size());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND request_id='racing-request'", Integer.class, tenant));
        } finally { pool.shutdownNow(); }
    }
    @Test void simultaneousDifferentGroupsCannotClaimTheSameOrder() throws Exception {
        var a = create(1); var b = create(2); var order = order("buyer");
        var pool = Executors.newFixedThreadPool(2); var latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (var target : List.of(a, b)) futures.add(pool.submit(() -> {
            ArynTenantContextHolder.setTenantId(tenant);
            try { latch.await(); service.change(tenant, "admin", target.groupId(), command("claim-" + target.groupId(), 0, "JOIN", order.getId())); return true; }
            catch (RuntimeException conflict) { return false; } finally { ArynTenantContextHolder.removeTenantId(); }
        }));
        try { latch.countDown(); int success = 0; for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) success++; assertEquals(1, success);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_order_link WHERE tenant_id=? AND status='ACTIVE'", Integer.class, tenant));
        } finally { pool.shutdownNow(); }
    }
    @Test void cutoffBlocksLateJoinAndDelayPreservesStableIdAndClosedState() {
        var group = create(1); var order = order("buyer");
        service.change(tenant, "admin", group.groupId(), command("close-request", 0, "CLOSE"));
        assertThrows(RuntimeException.class, () -> service.change(tenant, "admin", group.groupId(), command("late-request", 1, "JOIN", order.getId())));
        call.setDeliveryWindowStart(start.plusHours(1)); call.setDeliveryWindowEnd(start.plusHours(4));
        service.refreshCall(tenant, "change-1", "call"); service.refreshCall(tenant, "change-1", "call");
        var updated = service.detail(tenant, "admin", group.groupId()).group();
        assertEquals("CLOSED", updated.getStatus()); assertEquals(start.plusHours(1), updated.getWindowStart()); assertEquals(2L, updated.getRevision());
        assertEquals(group.groupId(), updated.getId()); assertEquals(1, updated.getWindowSequence());
    }
    @Test void missingScheduleRequiresExplicitPlanningAndFullCancellationTouchesOnlyOwnLink() {
        var unscheduled = service.create(tenant, "admin", new Create("unscheduled-request", warehouse, "call", 1, null, null, null, 10, "等待排程"));
        var a = order("buyer-a"); var b = order("buyer-b");
        assertThrows(RuntimeException.class, () -> service.change(tenant, "admin", unscheduled.groupId(), command("no-cutoff-request", 0, "JOIN", a.getId())));
        var group = create(2); service.change(tenant, "admin", group.groupId(), command("join-request", 0, "JOIN", a.getId(), b.getId()));
        a.setStatus("11"); orders.updateById(a); service.reconcile(tenant); service.reconcile(tenant);
        assertEquals(1, service.detail(tenant, "admin", group.groupId()).orders().size()); assertEquals("buyer-b", orders.selectById(b.getId()).getUserId());
        assertTrue(service.buyerSchedule(tenant, "buyer-a", a.getId()).needsScheduling());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND action='ORDER_CANCELLED'", Integer.class, tenant));
    }
    private FulfillmentWave wave() {
        var input = new com.aryn.cloud.order.api.dto.FulfillmentWaveCreateDTO(); input.setWarehouseId(warehouse); input.setVesselCallId("call"); input.setPortCode("port"); input.setPlanDeliveryTime(start);
        return fulfillment.createWave(tenant, "admin", "仓管", input);
    }
    private void line(OrderInfo order) {
        jdbc.update("INSERT INTO order_item(id,order_id,sku_id,buy_quantity,tenant_id,del_flag) VALUES(?,?,?,?,?,'0')", UUID.randomUUID().toString(), order.getId(), "sku", 2, tenant);
    }
    @Test void multipleOrdersStayPendingUntilExplicitStartAndCannotJoinAfterExecution() {
        var group = create(1); var a = order("buyer-a"); var b = order("buyer-b"); line(a); line(b);
        service.change(tenant, "admin", group.groupId(), command("join-request", 0, "JOIN", a.getId(), b.getId()));
        var wave = wave(); assertEquals(2, fulfillment.addOrdersToWave(tenant, wave.getId(), List.of(a.getId(), b.getId())).size());
        assertEquals(2, fulfillment.addOrdersToWave(tenant, wave.getId(), List.of(a.getId(), b.getId())).size());
        assertEquals("1", jdbc.queryForObject("SELECT status FROM fulfillment_wave WHERE id=?", String.class, wave.getId()));
        assertThrows(RuntimeException.class, () -> fulfillment.startPicking(tenant, "admin", wave.getId()));
        service.change(tenant, "admin", group.groupId(), command("close-request", 1, "CLOSE"));
        assertEquals("2", fulfillment.startPicking(tenant, "admin", wave.getId()).getStatus());
        assertEquals("FULFILLING", service.detail(tenant, "admin", group.groupId()).group().getStatus());
        var later = order("buyer-c"); line(later);
        assertThrows(RuntimeException.class, () -> fulfillment.addOrderToWave(tenant, wave.getId(), later.getId()));
        assertEquals(2, fulfillment.listWaveItems(tenant, wave.getId()).size());
        assertEquals("2", orders.selectById(a.getId()).getStatus());
    }
    @Test void invalidBatchRollsBackEarlierItemsAndWarehouseBinding() {
        var a = order("buyer-a"); var b = order("buyer-b"); line(a); line(b);
        var sorted = new ArrayList<>(List.of(a,b)); sorted.sort(Comparator.comparing(OrderInfo::getId));
        var invalid = sorted.get(1); invalid.setVesselCallId("other-call"); orders.updateById(invalid);
        var wave = wave();
        assertThrows(RuntimeException.class, () -> fulfillment.addOrdersToWave(tenant, wave.getId(), List.of(a.getId(), b.getId())));
        assertTrue(fulfillment.listWaveItems(tenant, wave.getId()).isEmpty());
        assertNull(orders.selectById(sorted.get(0).getId()).getFulfillmentWarehouseId());
        var group = create(1); var fresh = order("buyer-c"); fresh.setFulfillmentWarehouseId("other-warehouse"); orders.updateById(fresh);
        assertThrows(RuntimeException.class, () -> service.change(tenant, "admin", group.groupId(), command("cross-warehouse-request", 0, "JOIN", fresh.getId())));
        assertTrue(service.detail(tenant, "admin", group.groupId()).orders().isEmpty());
    }
    @Test void joiningAfterWaveAssignmentPreservesPendingWaveAndRejectsStartedWave() {
        var a = order("buyer-a"); line(a); var pending = wave();
        fulfillment.addOrderToWave(tenant, pending.getId(), a.getId());
        var group = create(1);
        service.change(tenant, "admin", group.groupId(), command("adopt-wave-request", 0, "JOIN", a.getId()));
        assertEquals(pending.getId(), service.detail(tenant, "admin", group.groupId()).orders().get(0).getWaveId());
        assertEquals(0L, service.list(tenant, "admin", "call", null).stream().filter(item -> item.getId().equals(group.groupId())).findFirst().orElseThrow().getUnassignedCount());
        var b = order("buyer-b"); line(b); var started = wave();
        fulfillment.addOrderToWave(tenant, started.getId(), b.getId()); fulfillment.startPicking(tenant, "admin", started.getId());
        assertThrows(RuntimeException.class, () -> service.change(tenant, "admin", group.groupId(), command("reject-started-wave", 1, "JOIN", b.getId())));
        assertEquals(1, service.detail(tenant, "admin", group.groupId()).orders().size());
    }
    @Test void oneGroupCanSplitAcrossWavesWithoutSharingTheirExecutionState() {
        var group = create(1); var a = order("buyer-a"); var b = order("buyer-b"); line(a); line(b);
        service.change(tenant, "admin", group.groupId(), command("split-join-request", 0, "JOIN", a.getId(), b.getId()));
        var first = wave(); fulfillment.addOrderToWave(tenant, first.getId(), a.getId());
        service.change(tenant, "admin", group.groupId(), command("split-close-request", 1, "CLOSE"));
        fulfillment.startPicking(tenant, "admin", first.getId());
        var second = wave(); fulfillment.addOrderToWave(tenant, second.getId(), b.getId());
        assertEquals("1", jdbc.queryForObject("SELECT status FROM fulfillment_wave WHERE id=?", String.class, second.getId()));
        fulfillment.startPicking(tenant, "admin", second.getId());
        assertEquals(2, service.detail(tenant, "admin", group.groupId()).orders().stream().map(DeliveryGroupOrderLink::getWaveId).distinct().count());
        assertEquals("2", orders.selectById(a.getId()).getStatus()); assertEquals("2", orders.selectById(b.getId()).getStatus());
    }
    @Test void outboxRetriesKeepStablePerOrderEventsAndPersistAcknowledgement() {
        var group = create(1); var a = order("buyer-a"); var b = order("buyer-b");
        service.change(tenant, "admin", group.groupId(), command("join-request", 0, "JOIN", a.getId(), b.getId()));
        var ok = new org.apache.rocketmq.client.producer.SendResult(); ok.setSendStatus(org.apache.rocketmq.client.producer.SendStatus.SEND_OK);
        when(mq.syncSend(anyString(), org.mockito.ArgumentMatchers.<Object>any(), eq(5000L)))
            .thenReturn(ok).thenThrow(new IllegalStateException("broker unavailable")).thenReturn(ok);
        outbox.dispatch();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND outbox_status='PENDING'", Integer.class, tenant));
        jdbc.update("UPDATE delivery_group_operation SET next_attempt_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE tenant_id=? AND outbox_status='PENDING'", tenant);
        outbox.dispatch(); outbox.dispatch();
        var sent = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(mq, times(4)).syncSend(anyString(), sent.capture(), eq(5000L));
        var first = (com.aryn.cloud.message.api.dto.MessageSendCommand) sent.getAllValues().get(0);
        var retried = (com.aryn.cloud.message.api.dto.MessageSendCommand) sent.getAllValues().get(2);
        assertEquals(first.getEventId(), retried.getEventId()); assertEquals(first.getRecipientId(), retried.getRecipientId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND outbox_status='PENDING'", Integer.class, tenant));
        assertEquals("2", orders.selectById(a.getId()).getStatus());
    }
    @Test void paidOrderChoosesNextOpenWindowAndManualRemovalIsNotUndone() {
        var first = create(1); var second = create(2); var a = order("buyer-a");
        service.change(tenant, "admin", first.groupId(), command("cutoff-first-request", 0, "CLOSE"));
        service.assignEligible(tenant); service.assignEligible(tenant);
        assertEquals(second.groupId(), service.buyerSchedule(tenant, "buyer-a", a.getId()).groupId());
        service.change(tenant, "admin", second.groupId(), command("manual-remove-request", 1, "REMOVE", a.getId()));
        service.assignEligible(tenant); assertTrue(service.buyerSchedule(tenant, "buyer-a", a.getId()).needsScheduling());
        assertEquals(0, service.detail(tenant, "admin", second.groupId()).orders().size());
    }
    @Test void changedCallDetachesOnlyUnassignedOrderAndNotifiesOnce() {
        var group = create(1); var a = order("buyer-a"); var b = order("buyer-b");
        service.change(tenant, "admin", group.groupId(), command("context-join-request", 0, "JOIN", a.getId(), b.getId()));
        a.setVesselCallId("new-call"); orders.updateById(a); service.reconcile(tenant); service.reconcile(tenant);
        assertTrue(service.buyerSchedule(tenant, "buyer-a", a.getId()).needsScheduling());
        assertEquals(group.groupId(), service.buyerSchedule(tenant, "buyer-b", b.getId()).groupId());
        assertEquals(1, service.detail(tenant, "admin", group.groupId()).orders().size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND action='ORDER_CONTEXT_CHANGED'", Integer.class, tenant));
    }
    @Test void changedCallWithPendingWaveRemainsTraceableAndCannotStart() {
        var group = create(1); var a = order("buyer-a"); line(a);
        service.change(tenant, "admin", group.groupId(), command("wave-context-join", 0, "JOIN", a.getId()));
        var wave = wave(); fulfillment.addOrderToWave(tenant, wave.getId(), a.getId());
        service.change(tenant, "admin", group.groupId(), command("wave-context-close", 1, "CLOSE"));
        a.setVesselCallId("changed"); orders.updateById(a); service.reconcile(tenant); service.reconcile(tenant);
        assertEquals(wave.getId(), service.detail(tenant, "admin", group.groupId()).orders().get(0).getWaveId());
        var schedule = service.buyerSchedule(tenant, "buyer-a", a.getId()); assertTrue(schedule.needsScheduling()); assertNull(schedule.windowStart());
        assertThrows(RuntimeException.class, () -> fulfillment.startPicking(tenant, "admin", wave.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_group_operation WHERE tenant_id=? AND action='ORDER_CONTEXT_CHANGED'", Integer.class, tenant));
    }
    @Test void checkoutShowsOnlyAuthorizedPublicScheduleAndFallsBackAfterCutoff() {
        var group = create(1); when(vessels.isVesselMember(tenant, "ship", "buyer")).thenReturn(true);
        var schedule = service.checkoutSchedule(tenant, "buyer", "ship", "call");
        assertEquals(start, schedule.windowStart()); assertFalse(schedule.needsScheduling());
        assertThrows(RuntimeException.class, () -> service.checkoutSchedule(tenant, "outsider", "ship", "call"));
        service.change(tenant, "admin", group.groupId(), command("checkout-close-request", 0, "CLOSE"));
        assertTrue(service.checkoutSchedule(tenant, "buyer", "ship", "call").needsScheduling());
    }
}
