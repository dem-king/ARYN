package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PageDesignMetricDaily;
import com.aryn.cloud.promotion.mapper.PageDesignMetricDailyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignMetricServiceTest {

	@Mock
	private PageDesignMetricDailyMapper metricMapper;

	private PageDesignMetricService service;

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		service = new PageDesignMetricService(metricMapper);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void reportAggregatesEventsWithTenantScope() {
		List<PageDesignMetricService.MetricEvent> events = List.of(
				new PageDesignMetricService.MetricEvent("page_view", "page-1", "version-2", "-", "weapp", "2026-09-06"),
				new PageDesignMetricService.MetricEvent("component_click", "page-1", "version-2", "bottom-nav", null,
						null),
				new PageDesignMetricService.MetricEvent("render_error", "page-1", "version-2", "video-live", null, null),
				new PageDesignMetricService.MetricEvent("unknown", "page-1", "version-2", "-", null, null),
				new PageDesignMetricService.MetricEvent("page_view", "", "version-2", "-", null, null));

		int accepted = service.report(events);

		assertEquals(3, accepted);
		ArgumentCaptor<String> componentCaptor = ArgumentCaptor.forClass(String.class);
		verify(metricMapper, times(3)).upsertMetric(anyString(), eq("page-1"), eq("version-2"),
				eq(LocalDate.parse("2026-09-06")), componentCaptor.capture(), anyLong(), anyLong(), anyLong(),
				eq("tenant-1"));
		assertEquals(List.of("-", "bottom-nav", "video-live"), componentCaptor.getAllValues());
	}

	@Test
	void reportDefaultsToCurrentDate() {
		when(metricMapper.upsertMetric(anyString(), anyString(), anyString(), any(), anyString(), anyLong(),
				anyLong(), anyLong(), anyString())).thenReturn(1);

		service.report(List.of(new PageDesignMetricService.MetricEvent("page_view", "page-1", null, null, null, null)));

		verify(metricMapper).upsertMetric(anyString(), eq("page-1"), eq("-"), eq(LocalDate.now()),
				eq(PageDesignMetricDaily.COMPONENT_PAGE), eq(1L), eq(0L), eq(0L), eq("tenant-1"));
	}

	@Test
	void reportIgnoresEmptyBatch() {
		assertEquals(0, service.report(null));
		assertEquals(0, service.report(List.of()));
		verify(metricMapper, never()).upsertMetric(anyString(), anyString(), anyString(), any(), anyString(),
				anyLong(), anyLong(), anyLong(), anyString());
	}

	@Test
	void listMetricsFiltersByTenantAndWindow() {
		when(metricMapper.selectList(any())).thenReturn(List.of(new PageDesignMetricDaily()));

		List<PageDesignMetricDaily> metrics = service.listMetrics("page-1", 7);

		assertEquals(1, metrics.size());
		verify(metricMapper).selectList(any());
	}

}
