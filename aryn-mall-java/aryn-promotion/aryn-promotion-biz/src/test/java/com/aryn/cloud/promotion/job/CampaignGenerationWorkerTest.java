package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.CampaignGenerationJob;
import com.aryn.cloud.promotion.service.impl.*;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignGenerationWorkerTest {
	@Test void duplicateScansAreBoundedTenantFailuresAreIsolatedAndExecutorContextIsRestored() throws Exception {
		var jobs=mock(CampaignGenerationStore.class);var generator=mock(CampaignGenerationService.class);
		var worker=new CampaignGenerationWorker(jobs,generator);var pool=Executors.newSingleThreadExecutor();
		var tenants=mock(RemoteTenantService.class);var a=new SysTenant();a.setId("a");var b=new SysTenant();b.setId("b");when(tenants.list()).thenReturn(List.of(a,b));
		var job=new CampaignGenerationJob();job.setId("job");when(jobs.due()).thenReturn(List.of(job));
		var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var complete=new CountDownLatch(2);var observed=new CopyOnWriteArrayList<String>();
		doAnswer(call->{
			observed.add(ArynTenantContextHolder.getTenantId());complete.countDown();
			if("a".equals(ArynTenantContextHolder.getTenantId())) {entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));throw new IllegalStateException("fixture failure");}return null;
		}).when(generator).process("job");
		ReflectionTestUtils.setField(worker,"executor",pool);ReflectionTestUtils.setField(worker,"tenants",tenants);
		try {
			pool.submit(ArynTenantContextHolder::removeTenantId).get(5,TimeUnit.SECONDS);ArynTenantContextHolder.setTenantId("caller");worker.scan();assertTrue(entered.await(5,TimeUnit.SECONDS));
			for(int i=0;i<20;i++){worker.scan();}release.countDown();assertTrue(complete.await(5,TimeUnit.SECONDS));
			assertNull(pool.submit(ArynTenantContextHolder::getTenantId).get(5,TimeUnit.SECONDS));assertEquals(List.of("a","b"),observed);assertEquals("caller",ArynTenantContextHolder.getTenantId());
		} finally {release.countDown();worker.close();ArynTenantContextHolder.removeTenantId();}
	}
}
