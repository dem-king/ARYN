package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.PageDesignMetricDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 页面装修每日聚合指标。
 * <p>
 * 聚合表未纳入租户拦截器白名单（原生 upsert 语句与拦截器改写冲突），
 * 租户维度由服务端显式写入并在查询时显式过滤。
 *
 * @author 雨滴kian
 * @date 2026/09/06
 */
@Mapper
public interface PageDesignMetricDailyMapper extends BaseMapper<PageDesignMetricDaily> {

	@Insert("INSERT INTO page_design_metric_daily "
			+ "(id, page_design_id, version_id, metric_date, component_type, view_count, click_count, error_count, tenant_id) "
			+ "VALUES (#{id}, #{pageDesignId}, #{versionId}, #{metricDate}, #{componentType}, #{viewCount}, "
			+ "#{clickCount}, #{errorCount}, #{tenantId}) "
			+ "ON DUPLICATE KEY UPDATE view_count = view_count + VALUES(view_count), "
			+ "click_count = click_count + VALUES(click_count), error_count = error_count + VALUES(error_count)")
	int upsertMetric(@Param("id") String id, @Param("pageDesignId") String pageDesignId,
			@Param("versionId") String versionId, @Param("metricDate") java.time.LocalDate metricDate,
			@Param("componentType") String componentType, @Param("viewCount") long viewCount,
			@Param("clickCount") long clickCount, @Param("errorCount") long errorCount,
			@Param("tenantId") String tenantId);

}
