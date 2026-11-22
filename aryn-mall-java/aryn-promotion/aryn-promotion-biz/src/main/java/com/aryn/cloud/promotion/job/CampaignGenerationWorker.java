package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.impl.CampaignGenerationService;
import com.aryn.cloud.promotion.service.impl.CampaignGenerationStore;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class CampaignGenerationWorker {
	private final CampaignGenerationStore jobs;
	private final CampaignGenerationService generator;
	@DubboReference(check=false) private RemoteTenantService tenants;
	private final AtomicInteger start = new AtomicInteger();
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
	private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.AbortPolicy());

	@Scheduled(fixedDelayString="${aryn.campaign.scan-ms:5000}")
	public void scan() {
		try {
			var all = tenants.list(); if (all == null || all.isEmpty()) { return; }
			int first = Math.floorMod(start.getAndIncrement(), all.size());
			for (int i=0; i<all.size(); i++) { enqueue(all.get((first+i)%all.size()).getId()); }
		}
		catch (RuntimeException failed) { log.warn("活动生成队列租户扫描失败 errorType={}", failed.getClass().getSimpleName()); }
	}
	private void enqueue(String tenant) {
		if (tenant == null || !inFlight.add(tenant)) { return; }
		try {
			executor.submit(() -> {
				String previous = ArynTenantContextHolder.getTenantId(); ArynTenantContextHolder.setTenantId(tenant);
				try {
					var due = jobs.due();
					if (!due.isEmpty()) { generator.process(due.get(0).getId()); }
				}
				catch (RuntimeException failed) { log.warn("活动生成任务失败 tenant={} errorType={}", tenant, failed.getClass().getSimpleName()); }
				finally {
					if (previous == null) { ArynTenantContextHolder.removeTenantId(); } else { ArynTenantContextHolder.setTenantId(previous); }
					inFlight.remove(tenant);
				}
			});
		}
		catch (RejectedExecutionException full) { inFlight.remove(tenant); }
	}
	@PreDestroy public void close() { executor.shutdownNow(); }
}
