package com.aryn.cloud.product.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.support.CatalogIndexService;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 每租户最多一个在途任务；轮换扫描起点，避免队列满时尾部租户长期饥饿。 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class CatalogIndexWorker {
    private final CatalogIndexService service;
    @DubboReference(check = false) private RemoteTenantService tenants;
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger scanStart = new AtomicInteger();

    @Scheduled(fixedDelayString = "${aryn.ai.index-scan-ms:30000}")
    public void scan() {
        try {
            var all = tenants.list();
            if (all == null || all.isEmpty()) return;
            int start = Math.floorMod(scanStart.getAndIncrement(), all.size());
            for (int offset = 0; offset < all.size(); offset++) {
                String tenant = all.get((start + offset) % all.size()).getId();
                if (tenant == null || !inFlight.add(tenant)) continue;
                try {
                    executor.submit(() -> runTenant(tenant));
                } catch (RejectedExecutionException full) {
                    inFlight.remove(tenant);
                }
            }
        } catch (RuntimeException failure) {
            log.warn("目录索引租户列表读取失败；errorType={}", failure.getClass().getSimpleName());
        }
    }

    private void runTenant(String tenant) {
        String previous = ArynTenantContextHolder.getTenantId();
        ArynTenantContextHolder.setTenantId(tenant);
        try {
            service.refreshIfChanged();
            for (var generation : service.list()) {
                if ("QUEUED".equals(generation.getStatus()) || "BUILDING".equals(generation.getStatus())) {
                    service.build(generation.getId());
                    break;
                }
            }
        } catch (RuntimeException failure) {
            log.warn("目录索引租户任务失败；tenant={} errorType={}", tenant, failure.getClass().getSimpleName());
        } finally {
            if (previous == null) ArynTenantContextHolder.removeTenantId();
            else ArynTenantContextHolder.setTenantId(previous);
            inFlight.remove(tenant);
        }
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
