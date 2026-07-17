package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.mapper.PageDesignTemplateMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PageDesignTemplateServiceImpl {

	private final PageDesignTemplateMapper mapper;

	public List<PageDesignTemplate> listTemplates(String pageType) {
		return mapper.selectList(Wrappers.<PageDesignTemplate>lambdaQuery()
			.eq(PageDesignTemplate::getStatus, "0")
			.and(wrapper -> wrapper.eq(PageDesignTemplate::getPageType, "2")
				.or()
				.eq(PageDesignTemplate::getPageType, pageType))
			.orderByAsc(PageDesignTemplate::getSort)
			.orderByDesc(PageDesignTemplate::getCreateTime));
	}

	public PageDesignTemplate saveTenantTemplate(PageDesignTemplate template) {
		if (template.getId() != null) {
			PageDesignTemplate existing = requireMutableTemplate(template.getId());
			template.setSystemFlag(existing.getSystemFlag());
			template.setTenantId(existing.getTenantId());
			template.setSchemaVersion(2);
			mapper.updateById(template);
			return template;
		}
		template.setSystemFlag("0");
		template.setSchemaVersion(2);
		if (template.getStatus() == null) template.setStatus("0");
		if (template.getTemplateType() == null) template.setTemplateType("0");
		if (template.getPageType() == null) template.setPageType("2");
		mapper.insert(template);
		return template;
	}

	public boolean removeTenantTemplate(String id) {
		requireMutableTemplate(id);
		return mapper.deleteById(id) > 0;
	}

	private PageDesignTemplate requireMutableTemplate(String id) {
		PageDesignTemplate template = mapper.selectById(id);
		if (template == null) {
			throw new ArynBusinessException("模板不存在或无权访问");
		}
		if ("1".equals(template.getSystemFlag())) {
			throw new ArynBusinessException("系统模板不可修改或删除");
		}
		return template;
	}
}
