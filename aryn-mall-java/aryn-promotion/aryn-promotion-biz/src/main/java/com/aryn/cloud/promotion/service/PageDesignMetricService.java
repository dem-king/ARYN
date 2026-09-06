package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PageDesignMetricDaily;
import com.aryn.cloud.promotion.mapper.PageDesignMetricDailyMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 页面装修指标服务。
 * <p>
 * C 端埋点批量上报按 页面/版本/日期/组件 维度累加进聚合表；
 * 看板查询只读聚合表，不在装修请求链路执行重查询。
 *
 * @author 雨滴kian
 * @date 2026/09/06
 */
@Service
@RequiredArgsConstructor
public class PageDesignMetricService {

	private final PageDesignMetricDailyMapper metricMapper;

	/**
	 * 批量上报事件（page_view/component_click/render_error），单条非法事件跳过不阻断。
	 */
	public int report(List<MetricEvent> events) {
		if (events == null || events.isEmpty()) {
			return 0;
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		int accepted = 0;
		for (MetricEvent event : events) {
			if (event == null || !StringUtils.hasText(event.pageDesignId())) {
				continue;
			}
			LocalDate metricDate;
			try {
				metricDate = StringUtils.hasText(event.date()) ? LocalDate.parse(event.date()) : LocalDate.now();
			}
			catch (DateTimeParseException exception) {
				continue;
			}
			long views = "page_view".equals(event.action()) ? 1 : 0;
			long clicks = "component_click".equals(event.action()) ? 1 : 0;
			long errors = "render_error".equals(event.action()) ? 1 : 0;
			if (views + clicks + errors == 0) {
				continue;
			}
			metricMapper.upsertMetric(IdWorker.getIdStr(), event.pageDesignId(),
					StringUtils.hasText(event.versionId()) ? event.versionId() : "-",
					metricDate, StringUtils.hasText(event.componentType()) ? event.componentType()
							: PageDesignMetricDaily.COMPONENT_PAGE,
					views, clicks, errors, tenantId);
			accepted++;
		}
		return accepted;
	}

	/**
	 * 页面近 N 天聚合指标（含页面级与组件级）。
	 */
	public List<PageDesignMetricDaily> listMetrics(String pageDesignId, int days) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		LocalDate since = LocalDate.now().minusDays(Math.max(1, Math.min(days, 90)) - 1L);
		return metricMapper.selectList(Wrappers.<PageDesignMetricDaily>lambdaQuery()
			.eq(PageDesignMetricDaily::getPageDesignId, pageDesignId)
			.eq(PageDesignMetricDaily::getTenantId, tenantId)
			.ge(PageDesignMetricDaily::getMetricDate, since)
			.orderByDesc(PageDesignMetricDaily::getMetricDate));
	}

	/**
	 * 埋点事件。
	 *
	 * @param action        事件类型：page_view/component_click/render_error
	 * @param pageDesignId  页面ID
	 * @param versionId     发布版本ID，可为空
	 * @param componentType 组件类型，可为空
	 * @param terminal      终端：h5/weapp，可为空
	 * @param date          事件日期（yyyy-MM-dd），缺省为服务端当天
	 */
	public record MetricEvent(String action, String pageDesignId, String versionId, String componentType,
			String terminal, String date) {
	}

}
