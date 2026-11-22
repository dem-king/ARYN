package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import com.aryn.cloud.order.service.impl.DeliveryGroupOutboxService;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryGroupWorker {
    private final DeliveryGroupService groups;
    private final DeliveryGroupOutboxService outbox;
    @DubboReference(check = false) private RemoteTenantService tenants;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger start = new AtomicInteger();
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
    @Scheduled(fixedDelay = 15000)
    public void scan() {
        try {
            var all = tenants.list(); if (all == null || all.isEmpty()) return;
            int first = Math.floorMod(start.getAndIncrement(), all.size());
            for (int i = 0; i < all.size(); i++) enqueue(all.get((first + i) % all.size()).getId());
        } catch (RuntimeException failure) { log.warn("合送组租户扫描失败；errorType={}", failure.getClass().getSimpleName()); }
    }
    private void enqueue(String tenant) {
        if (tenant == null || !inFlight.add(tenant)) return;
        try {
            executor.submit(() -> {
                String previous = ArynTenantContextHolder.getTenantId(); ArynTenantContextHolder.setTenantId(tenant);
                try { groups.reconcile(tenant); groups.assignEligible(tenant); }
                catch (RuntimeException failure) { log.warn("合送组状态补偿失败；tenant={} errorType={}", tenant, failure.getClass().getSimpleName()); }
                try { outbox.dispatch(); }
                catch (RuntimeException failure) { log.warn("合送组通知扫描失败；tenant={} errorType={}", tenant, failure.getClass().getSimpleName()); }
                finally {
                    if (previous == null) ArynTenantContextHolder.removeTenantId(); else ArynTenantContextHolder.setTenantId(previous);
                    inFlight.remove(tenant);
                }
            });
        } catch (RejectedExecutionException full) { inFlight.remove(tenant); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
