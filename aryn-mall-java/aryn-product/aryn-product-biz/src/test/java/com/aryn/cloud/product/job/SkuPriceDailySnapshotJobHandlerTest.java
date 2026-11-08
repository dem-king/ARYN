package com.aryn.cloud.product.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.service.IGoodsSkuPriceDailyService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SKU 价格快照任务的租户迭代契约：逐租户设置上下文再快照（拦截器与显式条件
 * 双重保证）、单租户失败不阻断其他租户、结束后上下文清空。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SKU价格快照任务")
class SkuPriceDailySnapshotJobHandlerTest {

	@Mock
	private IGoodsSkuPriceDailyService goodsSkuPriceDailyService;

	@Mock
	private RemoteTenantService remoteTenantService;

	@InjectMocks
	private SkuPriceDailySnapshotJobHandler handler;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(handler, "remoteTenantService", remoteTenantService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("逐租户快照且以显式租户调用；结束后上下文清空")
	void iteratesTenantsWithExplicitContext() {
		SysTenant t1 = new SysTenant();
		t1.setId("t1");
		SysTenant t2 = new SysTenant();
		t2.setId("t2");
		when(remoteTenantService.list()).thenReturn(List.of(t1, t2));
		when(goodsSkuPriceDailyService.snapshotDaily(anyString(), any(LocalDate.class))).thenReturn(3);

		handler.skuPriceDailySnapshotJobHandler();

		verify(goodsSkuPriceDailyService).snapshotDaily(eq("t1"), any(LocalDate.class));
		verify(goodsSkuPriceDailyService).snapshotDaily(eq("t2"), any(LocalDate.class));
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	@DisplayName("单租户异常不阻断后续租户")
	void singleTenantFailureDoesNotBlockOthers() {
		SysTenant t1 = new SysTenant();
		t1.setId("t1");
		SysTenant t2 = new SysTenant();
		t2.setId("t2");
		when(remoteTenantService.list()).thenReturn(List.of(t1, t2));
		doThrow(new RuntimeException("boom")).when(goodsSkuPriceDailyService).snapshotDaily(eq("t1"), any());

		assertThatCode(() -> handler.skuPriceDailySnapshotJobHandler()).doesNotThrowAnyException();

		verify(goodsSkuPriceDailyService).snapshotDaily(eq("t2"), any(LocalDate.class));
	}

	@Test
	@DisplayName("无租户直接返回")
	void noTenantNoop() {
		when(remoteTenantService.list()).thenReturn(List.of());

		assertThatCode(() -> handler.skuPriceDailySnapshotJobHandler()).doesNotThrowAnyException();

		verify(goodsSkuPriceDailyService, never()).snapshotDaily(anyString(), any());
	}

}
