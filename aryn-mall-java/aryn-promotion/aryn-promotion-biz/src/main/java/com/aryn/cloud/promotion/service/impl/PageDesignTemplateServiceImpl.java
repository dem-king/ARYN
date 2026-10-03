package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignTemplateDTO;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.api.vo.PageDesignTemplateVO;
import com.aryn.cloud.promotion.mapper.PageDesignTemplateMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 页面装修模板服务实现。
 * <p>
 * 请求与响应统一以 JSON 对象承载模板内容，仅在持久化边界与实体 String 字段互转。
 *
 * @author 雨滴kian
 * @date 2026/09/22
 */
@Service
@RequiredArgsConstructor
public class PageDesignTemplateServiceImpl {

	private static final int DEFAULT_SCHEMA_VERSION = 2;

	private final PageDesignTemplateMapper mapper;

	public List<PageDesignTemplateVO> listTemplates(String pageType) {
		return mapper.selectList(Wrappers.<PageDesignTemplate>lambdaQuery()
				.eq(PageDesignTemplate::getStatus, "0")
				.and(wrapper -> wrapper.eq(PageDesignTemplate::getPageType, "2")
					.or()
					.eq(PageDesignTemplate::getPageType, pageType))
				.orderByAsc(PageDesignTemplate::getSort)
				.orderByDesc(PageDesignTemplate::getCreateTime))
			.stream()
			.map(PageDesignTemplateVO::from)
			.toList();
	}

	@Transactional(rollbackFor = Exception.class)
	public PageDesignTemplateVO saveTenantTemplate(PageDesignTemplateDTO request) {
		PageDesignTemplate template = new PageDesignTemplate();
		template.setTemplateName(request.getTemplateName());
		template.setTemplateType(request.getTemplateType());
		template.setPageType(request.getPageType());
		template.setTemplateContent(JSON.toJSONString(request.getTemplateContent()));
		template.setIndustryTag(request.getIndustryTag());
		template.setStatus(request.getStatus());
		template.setSort(request.getSort());

		template.setSchemaVersion(resolveSchemaVersion(request));

		if (request.getId() != null) {
			PageDesignTemplate existing = requireMutableTemplate(request.getId());
			template.setId(existing.getId());
			template.setSystemFlag(existing.getSystemFlag());
			mapper.updateById(template);
			return PageDesignTemplateVO.from(requireTemplate(request.getId()));
		}

		template.setSystemFlag("0");
		if (template.getStatus() == null) template.setStatus("0");
		if (template.getTemplateType() == null) template.setTemplateType("0");
		if (template.getPageType() == null) template.setPageType("2");
		mapper.insert(template);
		return PageDesignTemplateVO.from(template);
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean removeTenantTemplate(String id) {
		requireMutableTemplate(id);
		return mapper.deleteById(id) > 0;
	}

	/**
	 * 上架：将本租户模板发布到跨租户模板市场。
	 * <p>
	 * selectById 走正常租户过滤，跨租户模板查不到即抛错，天然满足「仅本租户模板可上架」。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void publishMarketTemplate(String id) {
		PageDesignTemplate template = requireMutableTemplate(id);
		if (!StringUtils.hasText(template.getTemplateContent())) {
			throw new ArynBusinessException("模板内容为空，不能上架到模板市场");
		}
		PageDesignTemplate update = new PageDesignTemplate();
		update.setId(id);
		update.setMarketStatus("1");
		mapper.updateById(update);
	}

	/**
	 * 下架：本租户模板从模板市场下架（market_status='2'）。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void offlineMarketTemplate(String id) {
		requireMutableTemplate(id);
		PageDesignTemplate update = new PageDesignTemplate();
		update.setId(id);
		update.setMarketStatus("2");
		mapper.updateById(update);
	}

	/**
	 * 市场模板分页列表（跨租户，仅已上架）。
	 *
	 * @param sortField 排序方式：createTime 按最新创建，其余（含空值）按下载量倒序
	 */
	public IPage<PageDesignTemplateVO> listMarketTemplates(long pageNum, long pageSize, String templateName,
			String industryTag, String sortField) {
		Page<PageDesignTemplate> page = new Page<>(pageNum, pageSize);
		return mapper.selectMarketTemplatePage(page, templateName, industryTag, sortField)
			.convert(PageDesignTemplateVO::fromMarketList);
	}

	/**
	 * 市场模板详情（跨租户，仅已上架，含完整内容用于预览/下载前确认）。
	 */
	public PageDesignTemplateVO getMarketTemplate(String id) {
		PageDesignTemplate template = mapper.selectMarketTemplateById(id);
		if (template == null) {
			throw new ArynBusinessException("市场模板不存在或未上架");
		}
		return PageDesignTemplateVO.from(template);
	}

	/**
	 * 下载市场模板：复制为当前租户的新模板，源模板下载量 +1。
	 * <p>
	 * 读源模板走跨租户 Mapper（@InterceptorIgnore）；插入新模板走正常租户拦截器，
	 * tenant_id 由拦截器自动注入为当前租户，market_status 归位为「未上架」。
	 */
	@Transactional(rollbackFor = Exception.class)
	public String downloadMarketTemplate(String id) {
		PageDesignTemplate source = mapper.selectMarketTemplateById(id);
		if (source == null) {
			throw new ArynBusinessException("市场模板不存在或未上架");
		}
		PageDesignTemplate copy = new PageDesignTemplate();
		copy.setTemplateName(source.getTemplateName());
		copy.setTemplateType(source.getTemplateType());
		copy.setPageType(source.getPageType());
		copy.setTemplateContent(source.getTemplateContent());
		copy.setSchemaVersion(source.getSchemaVersion());
		copy.setIndustryTag(source.getIndustryTag());
		copy.setStatus("0");
		copy.setSort(0);
		copy.setSystemFlag("0");
		copy.setMarketStatus("0");
		copy.setDownloadCount(0);
		mapper.insert(copy);
		mapper.increaseMarketDownloadCount(id);
		return copy.getId();
	}

	private Integer resolveSchemaVersion(PageDesignTemplateDTO request) {
		return request.getSchemaVersion() == null ? DEFAULT_SCHEMA_VERSION : request.getSchemaVersion();
	}

	private PageDesignTemplate requireTemplate(String id) {
		PageDesignTemplate template = mapper.selectById(id);
		if (template == null) {
			throw new ArynBusinessException("模板不存在或无权访问");
		}
		return template;
	}

	private PageDesignTemplate requireMutableTemplate(String id) {
		PageDesignTemplate template = requireTemplate(id);
		if ("1".equals(template.getSystemFlag())) {
			throw new ArynBusinessException("系统模板不可修改或删除");
		}
		return template;
	}
}
