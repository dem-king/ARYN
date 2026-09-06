package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignPublishDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import com.aryn.cloud.promotion.service.IPageDesignThemeService;
import com.aryn.cloud.promotion.service.PageDesignAuditService;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignPublishServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignVersionMapper versionMapper;

	@Mock
	private PageDesignDocumentValidator validator;

	@Mock
	private IPageDesignThemeService themeService;

	@Mock
	private PageDesignAuditService auditService;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RLock lock;

	@Mock
	private UserSupplier userSupplier;

	private PageDesignVersionServiceImpl service;

	@BeforeEach
	void setUp() throws InterruptedException {
		ArynTenantContextHolder.setTenantId("tenant-1");
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
		service = new PageDesignVersionServiceImpl(pageDesignMapper, versionMapper, validator, themeService,
				auditService, redisTemplate, redissonClient, userSupplier);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void publishCreatesNextImmutableVersionAndSwitchesPointer() {
		when(userSupplier.getCurrentUserName()).thenReturn("admin");
		PageDesign page = page("page-1", 4L, "{\"schemaVersion\":2,\"components\":[]}");
		PageDesignVersion previous = new PageDesignVersion();
		previous.setVersionNo(2);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(versionMapper.selectOne(any())).thenReturn(previous);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(themeService.embedThemeSnapshot(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(versionMapper.insert(any(PageDesignVersion.class))).thenReturn(1);
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);
		PageDesignPublishDTO request = new PageDesignPublishDTO();
		request.setId("page-1");
		request.setDraftRevision(4L);
		request.setPublishRemark("首发");

		PageDesignVersion published = service.publish(request);

		assertEquals(3, published.getVersionNo());
		assertEquals("首发", published.getPublishRemark());
		assertEquals("admin", published.getPublishBy());
		assertEquals("tenant-1", published.getTenantId());
		assertNotNull(published.getId());
		ArgumentCaptor<PageDesign> pageCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).update(pageCaptor.capture(), any());
		assertEquals("1", pageCaptor.getValue().getPublishedStatus());
		assertEquals(published.getId(), pageCaptor.getValue().getPublishedVersionId());
		verify(lock).unlock();
	}

	@Test
	void publishValidationFailureLeavesPublishedPointerUntouched() {
		PageDesign page = page("page-1", 4L, "{}");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate("{}")).thenReturn(List.of("schemaVersion必须为2"));
		PageDesignPublishDTO request = new PageDesignPublishDTO();
		request.setId("page-1");
		request.setDraftRevision(4L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.publish(request));

		assertTrue(error.getMsg().contains("schemaVersion"));
		verify(versionMapper, never()).insert(any(PageDesignVersion.class));
		verify(pageDesignMapper, never()).update(any(PageDesign.class), any());
	}

	@Test
	void rollbackCreatesNewVersionFromHistoricalSnapshot() {
		when(userSupplier.getCurrentUserName()).thenReturn("admin");
		PageDesign page = page("page-1", 7L, "{\"schemaVersion\":2}");
		PageDesignVersion historical = new PageDesignVersion();
		historical.setId("version-1");
		historical.setPageDesignId("page-1");
		historical.setVersionNo(1);
		historical.setSchemaVersion(2);
		historical.setPageName("旧首页");
		historical.setPageType("1");
		historical.setPageContent("{\"schemaVersion\":2,\"components\":[{\"id\":\"old\"}]}");
		PageDesignVersion latest = new PageDesignVersion();
		latest.setVersionNo(3);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(versionMapper.selectById("version-1")).thenReturn(historical);
		when(versionMapper.selectOne(any())).thenReturn(latest);
		when(themeService.embedThemeSnapshot(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		when(versionMapper.insert(any(PageDesignVersion.class))).thenReturn(1);
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);

		PageDesignVersion rolledBack = service.rollback("page-1", "version-1", "恢复旧版");

		assertEquals(4, rolledBack.getVersionNo());
		assertEquals(historical.getPageContent(), rolledBack.getPageContent());
		assertEquals("恢复旧版", rolledBack.getPublishRemark());
	}

	@Test
	void unpublishKeepsVersionHistory() {
		PageDesign page = page("page-1", 1L, "{}");
		page.setPublishedVersionId("version-2");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);

		assertTrue(service.unpublish("page-1"));

		ArgumentCaptor<PageDesign> pageCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).update(pageCaptor.capture(), any());
		assertEquals("0", pageCaptor.getValue().getPublishedStatus());
		verify(versionMapper, never()).delete(any());
	}

	private PageDesign page(String id, Long revision, String content) {
		PageDesign page = new PageDesign();
		page.setId(id);
		page.setPageName("首页");
		page.setPageType("1");
		page.setPageContent(content);
		page.setSchemaVersion(2);
		page.setDraftRevision(revision);
		return page;
	}

}
