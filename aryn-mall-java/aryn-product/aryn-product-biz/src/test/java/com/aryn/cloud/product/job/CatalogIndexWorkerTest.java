package com.aryn.cloud.product.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.support.CatalogIndexService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogIndexWorkerTest {
    @Test void oneTenantFailureDoesNotStarveOthersOrLeakContextOnThreadReuse() throws Exception {
        var service = mock(CatalogIndexService.class);
        var tenants = mock(RemoteTenantService.class);
        var a = new SysTenant(); a.setId("a");
        var b = new SysTenant(); b.setId("b");
        when(tenants.list()).thenReturn(List.of(a, b));
        var completed = new CountDownLatch(2);
        doAnswer(call -> {
            completed.countDown();
            if ("a".equals(ArynTenantContextHolder.getTenantId())) throw new IllegalStateException("tenant unavailable");
            return null;
        }).when(service).refreshIfChanged();
        when(service.list()).thenReturn(List.of());
        var worker = new CatalogIndexWorker(service);
        var pool = Executors.newSingleThreadExecutor();
        ReflectionTestUtils.setField(worker, "tenants", tenants);
        ReflectionTestUtils.setField(worker, "executor", pool);
        try {
            pool.submit(ArynTenantContextHolder::removeTenantId).get(5, TimeUnit.SECONDS);
            ArynTenantContextHolder.setTenantId("caller");
            worker.scan();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(pool.submit(ArynTenantContextHolder::getTenantId).get(5, TimeUnit.SECONDS));
            assertEquals("caller", ArynTenantContextHolder.getTenantId());
            verify(service, times(2)).refreshIfChanged();
        } finally { worker.close(); ArynTenantContextHolder.removeTenantId(); }
    }
}
