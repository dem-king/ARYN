package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import com.aryn.cloud.order.service.impl.DeliveryGroupOutboxService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryGroupWorkerTest {
    @Test void failuresDoNotSkipNotificationsOrLeakTenantAndWakeupsStaySingleFlight() throws Exception {
        var groups = mock(DeliveryGroupService.class); var outbox = mock(DeliveryGroupOutboxService.class);
        var worker = new DeliveryGroupWorker(groups, outbox); var pool = Executors.newSingleThreadExecutor();
        var tenants = mock(RemoteTenantService.class); var a = new SysTenant(); a.setId("a"); var b = new SysTenant(); b.setId("b");
        when(tenants.list()).thenReturn(List.of(a, b));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var completed = new CountDownLatch(2);
        var observed = new CopyOnWriteArrayList<String>();
        doAnswer(call -> { if ("a".equals(ArynTenantContextHolder.getTenantId())) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); throw new IllegalStateException("fixture failure"); } return null; }).when(groups).reconcile(anyString());
        doAnswer(call -> { observed.add(ArynTenantContextHolder.getTenantId()); completed.countDown(); return null; }).when(outbox).dispatch();
        ReflectionTestUtils.setField(worker, "executor", pool); ReflectionTestUtils.setField(worker, "tenants", tenants);
        try {
            pool.submit(ArynTenantContextHolder::removeTenantId).get(5, TimeUnit.SECONDS);
            ArynTenantContextHolder.setTenantId("caller"); worker.scan(); assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 5; i++) worker.scan(); release.countDown(); assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(pool.submit(ArynTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
            assertEquals(List.of("a", "b"), observed); assertEquals("caller", ArynTenantContextHolder.getTenantId());
            verify(groups).reconcile("a"); verify(groups).reconcile("b"); verify(groups).assignEligible("b");
        } finally { release.countDown(); worker.close(); ArynTenantContextHolder.removeTenantId(); }
    }
}
