package com.aryn.cloud.promotion.mapper;

import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PageDesignTemplateMapper extends BaseMapper<PageDesignTemplate> {

	/**
	 * 跨租户分页查询市场模板列表（仅 market_status='1' 且未删除）。
	 * <p>
	 * 模板市场是跨租户共享资源，必须绕过租户行级拦截器；
	 * 该绕过已在 TenantInterceptorBypassAuditTest 白名单中登记评审。
	 *
	 * @param sortField 排序字段，仅接受 downloadCount / createTime，其余值回落到按下载量
	 */
	@InterceptorIgnore(tenantLine = "true")
	IPage<PageDesignTemplate> selectMarketTemplatePage(Page<PageDesignTemplate> page,
			@Param("templateName") String templateName, @Param("industryTag") String industryTag,
			@Param("sortField") String sortField);

	/**
	 * 跨租户读取单个已上架市场模板（预览/下载前确认用）。
	 */
	@InterceptorIgnore(tenantLine = "true")
	PageDesignTemplate selectMarketTemplateById(@Param("id") String id);

	/**
	 * 跨租户对源市场模板下载量 +1（源模板属于其他租户，UPDATE 需绕过租户拦截）。
	 */
	@InterceptorIgnore(tenantLine = "true")
	int increaseMarketDownloadCount(@Param("id") String id);
}
