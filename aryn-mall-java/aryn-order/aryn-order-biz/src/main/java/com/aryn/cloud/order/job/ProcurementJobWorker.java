package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.impl.*;
import com.aryn.cloud.order.support.procurement.ProcurementQueued;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ProcurementJobWorker {
    private final ProcurementJobService jobs;
    private final ProcurementProcessor processor;
    @DubboReference(check = false) private RemoteTenantService tenants;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final AtomicInteger start = new AtomicInteger();
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());
    @EventListener public void wake(ProcurementQueued event) { enqueue(event.tenantId()); }
    @Scheduled(fixedDelayString = "${aryn.procurement.scan-ms:5000}")
    public void scan() {
        try {
            var all = tenants.list(); if (all == null || all.isEmpty()) return;
            int first = Math.floorMod(start.getAndIncrement(), all.size());
            for (int i = 0; i < all.size(); i++) enqueue(all.get((first + i) % all.size()).getId());
        } catch (RuntimeException failure) { log.warn("采购队列租户扫描失败；errorType={}", failure.getClass().getSimpleName()); }
    }
    private void enqueue(String tenant) {
        if (tenant == null || !inFlight.add(tenant)) return;
        try {
            executor.submit(() -> {
                String previous = ArynTenantContextHolder.getTenantId(); ArynTenantContextHolder.setTenantId(tenant);
                try {
                    var due = jobs.due();
                    // 每轮每租户一批；持久队列和轮换起点保障其他租户得到执行机会。
                    if (!due.isEmpty()) processor.process(due.get(0).getId());
                } catch (RuntimeException failure) { log.warn("采购租户任务失败；tenant={} errorType={}", tenant, failure.getClass().getSimpleName()); }
                finally {
                    if (previous == null) ArynTenantContextHolder.removeTenantId(); else ArynTenantContextHolder.setTenantId(previous);
                    inFlight.remove(tenant);
                }
            });
        } catch (RejectedExecutionException full) { inFlight.remove(tenant); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
