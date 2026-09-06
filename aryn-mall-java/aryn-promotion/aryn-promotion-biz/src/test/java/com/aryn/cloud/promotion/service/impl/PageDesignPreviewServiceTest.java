package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.AppPageDesignVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import com.aryn.cloud.promotion.service.PageDesignPreviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignPreviewServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignVersionMapper versionMapper;

	@Mock
	private com.aryn.cloud.promotion.mapper.PageDesignReleaseMapper releaseMapper;

	@Mock
	private com.aryn.cloud.promotion.mapper.PageDesignReleaseTargetMapper releaseTargetMapper;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private PageDesignPreviewService service;

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		service = new PageDesignPreviewService(pageDesignMapper, versionMapper, releaseMapper, releaseTargetMapper,
				redisTemplate);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void publishedReadUsesImmutableVersionInsteadOfDraft() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		PageDesign page = publishedPage();
		page.setPageContent("{\"schemaVersion\":2,\"components\":[{\"id\":\"draft\"}]}");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(versionMapper.selectById("version-3")).thenReturn(publishedVersion());

		AppPageDesignVO result = service.getPublished("page-1");

		assertEquals("published", result.getPageContent().getJSONArray("components").getJSONObject(0).getString("id"));
		assertEquals("version-3", result.getPublishedVersionId());
		assertEquals(3, result.getPublishedVersionNo());
	}

	@Test
	void unpublishedPageNeverExposesDraft() {
		PageDesign page = publishedPage();
		page.setPublishedStatus("0");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		assertThrows(ArynBusinessException.class, () -> service.getPublished("page-1"));

		verify(versionMapper, never()).selectById(anyString());
	}

	@Test
	void previewTokenIsOpaqueAndBindsTenantPageAndRevision() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		PageDesign page = publishedPage();
		page.setDraftRevision(8L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		String token = service.createPreviewToken("page-1", 8L);

		assertNotEquals("tenant-1:page-1:8", token);
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(key.capture(), value.capture(), eq(10L), eq(TimeUnit.MINUTES));
		assertEquals("tenant-1", JSON.parseObject(value.getValue()).getString("tenantId"));
		assertEquals("page-1", JSON.parseObject(value.getValue()).getString("pageId"));
		assertEquals(8L, JSON.parseObject(value.getValue()).getLongValue("draftRevision"));
	}

	@Test
	void validPreviewTokenReturnsBoundDraftRevision() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		PageDesign page = publishedPage();
		page.setDraftRevision(8L);
		page.setPageContent("{\"schemaVersion\":2,\"components\":[{\"id\":\"draft\"}]}");
		when(valueOperations.get("page_design_preview:token-1"))
			.thenReturn("{\"tenantId\":\"tenant-1\",\"pageId\":\"page-1\",\"draftRevision\":8}");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		AppPageDesignVO result = service.getPreview("token-1");

		assertEquals("draft", result.getPageContent().getJSONArray("components").getJSONObject(0).getString("id"));
		assertEquals(8L, result.getDraftRevision());
	}

	@Test
	void expiredOrCrossTenantPreviewTokenFails() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("page_design_preview:expired")).thenReturn(null);
		when(valueOperations.get("page_design_preview:other"))
			.thenReturn("{\"tenantId\":\"tenant-2\",\"pageId\":\"page-1\",\"draftRevision\":8}");

		assertThrows(ArynBusinessException.class, () -> service.getPreview("expired"));
		assertThrows(ArynBusinessException.class, () -> service.getPreview("other"));

		verify(pageDesignMapper, never()).selectById(anyString());
	}

	@Test
	void previewTokenFailsAfterDraftRevisionChanges() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		PageDesign page = publishedPage();
		page.setDraftRevision(9L);
		when(valueOperations.get("page_design_preview:stale"))
			.thenReturn("{\"tenantId\":\"tenant-1\",\"pageId\":\"page-1\",\"draftRevision\":8}");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		assertThrows(ArynBusinessException.class, () -> service.getPreview("stale"));
	}

	private PageDesign publishedPage() {
		PageDesign page = new PageDesign();
		page.setId("page-1");
		page.setPageName("Home");
		page.setPageType("1");
		page.setSchemaVersion(2);
		page.setDraftRevision(7L);
		page.setPublishedStatus("1");
		page.setPublishedVersionId("version-3");
		return page;
	}

	private PageDesignVersion publishedVersion() {
		PageDesignVersion version = new PageDesignVersion();
		version.setId("version-3");
		version.setPageDesignId("page-1");
		version.setPageName("Published home");
		version.setPageType("1");
		version.setSchemaVersion(2);
		version.setVersionNo(3);
		version.setPageContent("{\"schemaVersion\":2,\"components\":[{\"id\":\"published\",\"type\":\"unknown\"}]}");
		return version;

	}

}
