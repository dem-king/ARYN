package com.aryn.cloud.promotion.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.AppPageDesignVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PageDesignPreviewService {

	private static final String PAGE_CACHE_PREFIX = "page_design_cache:";
	private static final String PREVIEW_TOKEN_PREFIX = "page_design_preview:";
	private static final long PAGE_CACHE_TTL_HOURS = 24L;
	private static final long PREVIEW_TOKEN_TTL_MINUTES = 10L;

	private final PageDesignMapper pageDesignMapper;
	private final PageDesignVersionMapper versionMapper;
	private final StringRedisTemplate redisTemplate;

	public AppPageDesignVO getPublishedHome() {
		PageDesign page = pageDesignMapper.selectOne(Wrappers.<PageDesign>lambdaQuery()
			.eq(PageDesign::getPageType, "1")
			.eq(PageDesign::getHomeStatus, CommonConstants.YES)
			.eq(PageDesign::getPublishedStatus, "1")
			.last("limit 1"));
		return getPublishedPage(page);
	}

	public AppPageDesignVO getPublished(String pageId) {
		return getPublishedPage(pageDesignMapper.selectById(pageId));
	}

	public String createPreviewToken(String pageId, Long draftRevision) {
		PageDesign page = requirePage(pageId);
		if (!Objects.equals(page.getDraftRevision(), draftRevision)) {
			throw new ArynBusinessException("Draft revision has changed; reload before previewing");
		}
		String token = UUID.randomUUID().toString().replace("-", "");
		PreviewBinding binding = new PreviewBinding(ArynTenantContextHolder.getTenantId(), pageId, draftRevision);
		redisTemplate.opsForValue().set(PREVIEW_TOKEN_PREFIX + token, JSON.toJSONString(binding),
				PREVIEW_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
		return token;
	}

	public AppPageDesignVO getPreview(String token) {
		if (!StringUtils.hasText(token)) {
			throw invalidPreviewToken();
		}
		String stored = redisTemplate.opsForValue().get(PREVIEW_TOKEN_PREFIX + token);
		if (!StringUtils.hasText(stored)) {
			throw invalidPreviewToken();
		}
		PreviewBinding binding;
		try {
			binding = JSON.parseObject(stored, PreviewBinding.class);
		}
		catch (RuntimeException exception) {
			throw invalidPreviewToken();
		}
		if (binding == null || !Objects.equals(ArynTenantContextHolder.getTenantId(), binding.tenantId())) {
			throw invalidPreviewToken();
		}
		PageDesign page = requirePage(binding.pageId());
		if (!Objects.equals(page.getDraftRevision(), binding.draftRevision())) {
			throw new ArynBusinessException("Preview expired because the draft changed");
		}
		return fromDraft(page);
	}

	private AppPageDesignVO getPublishedPage(PageDesign page) {
		if (page == null || !"1".equals(page.getPublishedStatus())
				|| !StringUtils.hasText(page.getPublishedVersionId())) {
			throw new ArynBusinessException("Published page does not exist");
		}
		String cacheKey = PAGE_CACHE_PREFIX + ArynTenantContextHolder.getTenantId() + ":" + page.getId() + ":"
				+ page.getPublishedVersionId();
		String cached = redisTemplate.opsForValue().get(cacheKey);
		if (StringUtils.hasText(cached)) {
			return JSON.parseObject(cached, AppPageDesignVO.class);
		}
		PageDesignVersion version = versionMapper.selectById(page.getPublishedVersionId());
		if (version == null || !Objects.equals(page.getId(), version.getPageDesignId())) {
			throw new ArynBusinessException("Published page version does not exist");
		}
		AppPageDesignVO result = fromVersion(version);
		redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(result), PAGE_CACHE_TTL_HOURS, TimeUnit.HOURS);
		return result;
	}

	private PageDesign requirePage(String pageId) {
		PageDesign page = pageDesignMapper.selectById(pageId);
		if (page == null) {
			throw new ArynBusinessException("Page does not exist or cannot be accessed");
		}
		return page;
	}

	private AppPageDesignVO fromVersion(PageDesignVersion version) {
		AppPageDesignVO result = new AppPageDesignVO();
		result.setId(version.getPageDesignId());
		result.setPageName(version.getPageName());
		result.setPageType(version.getPageType());
		result.setSchemaVersion(version.getSchemaVersion());
		result.setPageContent(parseContent(version.getPageContent()));
		result.setPublishedVersionId(version.getId());
		result.setPublishedVersionNo(version.getVersionNo());
		result.setPublishedAt(version.getPublishedAt());
		return result;
	}

	private AppPageDesignVO fromDraft(PageDesign page) {
		AppPageDesignVO result = new AppPageDesignVO();
		result.setId(page.getId());
		result.setPageName(page.getPageName());
		result.setPageType(page.getPageType());
		result.setSchemaVersion(page.getSchemaVersion());
		result.setPageContent(parseContent(page.getPageContent()));
		result.setDraftRevision(page.getDraftRevision());
		result.setPublishedVersionId(page.getPublishedVersionId());
		result.setPublishedAt(page.getPublishedAt());
		return result;
	}

	private JSONObject parseContent(String pageContent) {
		try {
			return JSON.parseObject(pageContent);
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("Page decoration content is invalid");
		}
	}

	private ArynBusinessException invalidPreviewToken() {
		return new ArynBusinessException("Preview token is invalid or expired");
	}

	private record PreviewBinding(String tenantId, String pageId, Long draftRevision) {
	}

}
