package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.ProcurementSourceBatch;
import com.aryn.cloud.order.service.impl.*;
import com.aryn.cloud.order.support.procurement.ProcurementQueued;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcurementJobWorkerTest {
    @Test void duplicateWakeupsDoNotRunTenantConcurrentlyAndThreadReuseRestoresContext() throws Exception {
        var jobs = mock(ProcurementJobService.class); var processor = mock(ProcurementProcessor.class);
        var worker = new ProcurementJobWorker(jobs, processor); var pool = Executors.newSingleThreadExecutor();
        var tenants = mock(RemoteTenantService.class); var a = new SysTenant(); a.setId("a"); var b = new SysTenant(); b.setId("b");
        when(tenants.list()).thenReturn(List.of(a, b));
        var batch = new ProcurementSourceBatch(); batch.setId("batch"); when(jobs.due()).thenReturn(List.of(batch));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var completed = new CountDownLatch(2);
        var observed = new java.util.concurrent.CopyOnWriteArrayList<String>();
        doAnswer(call -> {
            observed.add(ArynTenantContextHolder.getTenantId()); completed.countDown();
            if ("a".equals(ArynTenantContextHolder.getTenantId())) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); throw new IllegalStateException("unavailable"); }
            return null;
        }).when(processor).process("batch");
        ReflectionTestUtils.setField(worker, "executor", pool); ReflectionTestUtils.setField(worker, "tenants", tenants);
        try {
            pool.submit(ArynTenantContextHolder::removeTenantId).get(5, TimeUnit.SECONDS);
            ArynTenantContextHolder.setTenantId("caller"); worker.wake(new ProcurementQueued("a")); assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20; i++) worker.wake(new ProcurementQueued("a"));
            worker.scan(); release.countDown(); assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(pool.submit(ArynTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
            assertEquals(List.of("a", "b"), observed); assertEquals("caller", ArynTenantContextHolder.getTenantId());
        } finally { release.countDown(); worker.close(); ArynTenantContextHolder.removeTenantId(); }
    }
}
