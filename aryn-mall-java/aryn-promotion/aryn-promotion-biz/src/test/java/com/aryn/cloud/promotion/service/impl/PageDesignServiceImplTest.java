package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.vo.PageDesignEditorVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.service.PageDesignAuditService;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignServiceImplTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RLock lock;

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignAuditService auditService;

	private PageDesignServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestPageDesignService(redisTemplate, redissonClient, auditService, pageDesignMapper);
	}

	private void mockAcquiredHomePageLock() throws InterruptedException {
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
	}

	@Test
	void getOrCreateHomePageCreatesOneDefaultPageInsideTenantLock() throws InterruptedException {
		mockAcquiredHomePageLock();
		when(pageDesignMapper.selectOne(any())).thenReturn(null);

		PageDesign result = service.getOrCreateHomePage();

		ArgumentCaptor<PageDesign> pageCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).insert(pageCaptor.capture());
		PageDesign inserted = pageCaptor.getValue();
		assertSame(inserted, result);
		assertEquals("首页", inserted.getPageName());
		assertEquals("1", inserted.getPageType());
		assertEquals("1", inserted.getHomeStatus());
		assertEquals("0", inserted.getStatus());
		assertEquals("{\"components\":[]}", inserted.getPageContent());
		verify(lock).unlock();
	}

	@Test
	void getOrCreateHomePageEvictsNegativeCacheAfterCreatingPage() throws InterruptedException {
		ArynTenantContextHolder.setTenantId("tenant-with-negative-cache");
		try {
			mockAcquiredHomePageLock();
			when(pageDesignMapper.selectOne(any())).thenReturn(null);

			service.getOrCreateHomePage();

			verify(redisTemplate)
				.delete(CacheConstants.HOME_PAGE_DESIGN_CACHE + "tenant-with-negative-cache");
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	@Test
	void concurrentGetOrCreateHomePageInsertsOnlyOnce() throws Exception {
		ReentrantLock tenantLock = new ReentrantLock();
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> tenantLock
			.tryLock(invocation.getArgument(0), invocation.getArgument(1)));
		doAnswer(invocation -> {
			tenantLock.unlock();
			return null;
		}).when(lock).unlock();

		AtomicReference<PageDesign> storedPage = new AtomicReference<>();
		when(pageDesignMapper.selectOne(any())).thenAnswer(invocation -> {
			PageDesign snapshot = storedPage.get();
			if (snapshot == null) {
				Thread.sleep(100);
			}
			return snapshot;
		});
		when(pageDesignMapper.insert(any(PageDesign.class))).thenAnswer(invocation -> {
			storedPage.set(invocation.getArgument(0));
			return 1;
		});

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<PageDesign> task = () -> {
			ArynTenantContextHolder.setTenantId("concurrent-tenant");
			try {
				ready.countDown();
				assertTrue(start.await(5, TimeUnit.SECONDS));
				return service.getOrCreateHomePage();
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		};

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<PageDesign> firstFuture = executor.submit(task);
			Future<PageDesign> secondFuture = executor.submit(task);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();

			PageDesign first = firstFuture.get(5, TimeUnit.SECONDS);
			PageDesign second = secondFuture.get(5, TimeUnit.SECONDS);

			assertSame(first, second);
			verify(pageDesignMapper, times(1)).insert(any(PageDesign.class));
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void updatePageDesignByIdSwitchesHomepageInsideTenantLock() throws InterruptedException {
		mockAcquiredHomePageLock();
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);
		when(pageDesignMapper.updateById(any(PageDesign.class))).thenReturn(1);
		PageDesign newHomePage = new PageDesign();
		newHomePage.setId("new-home");
		newHomePage.setHomeStatus("1");

		boolean updated = service.updatePageDesignById(newHomePage);

		assertTrue(updated);
		assertEquals("1", newHomePage.getPageType());
		ArgumentCaptor<PageDesign> resetCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).update(resetCaptor.capture(), any());
		assertEquals("0", resetCaptor.getValue().getHomeStatus());
		assertEquals("0", resetCaptor.getValue().getPageType());
		verify(lock).unlock();
	}

	@Test
	void updatePageDesignByIdEvictsHomepageCacheAfterTransactionCommit() {
		when(pageDesignMapper.updateById(any(PageDesign.class))).thenReturn(1);
		PageDesign pageDesign = new PageDesign();
		pageDesign.setId("home-page");
		TransactionSynchronizationManager.initSynchronization();
		try {
			assertTrue(service.updatePageDesignById(pageDesign));

			verify(redisTemplate, never()).delete(anyString());
			assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);
			verify(redisTemplate).delete(anyString());
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void getHomePageDoesNotUnlockWhenTenantLockWasNotAcquired() throws InterruptedException {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(anyString())).thenReturn(null);
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);

		assertThrows(ArynBusinessException.class, () -> service.getHomePage(new PageDesign()));

		verify(lock, never()).unlock();
	}

	@Test
	void draftRequestAcceptsStructuredPageContent() throws Exception {
		PageDesignDraftDTO draft = new ObjectMapper().readValue("""
				{
				  "pageName": "首页",
				  "pageContent": {"schemaVersion": 2, "components": []},
				  "schemaVersion": 2,
				  "draftRevision": 3
				}
				""", PageDesignDraftDTO.class);

		Object pageContent = draft.getPageContent();
		assertTrue(pageContent instanceof Map);
		assertEquals(2, ((Map<?, ?>) pageContent).get("schemaVersion"));
	}

	@Test
	void saveDraftAtomicallyIncrementsMatchingRevision() {
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);
		PageDesignDraftDTO draft = new PageDesignDraftDTO();
		draft.setId("page-1");
		draft.setPageName("夏日首页");
		draft.setPageContent(JSON.parseObject("{\"schemaVersion\":2,\"components\":[]}"));
		draft.setSchemaVersion(2);
		draft.setDraftRevision(3L);

		long revision = service.saveDraft(draft);

		assertEquals(4L, revision);
		ArgumentCaptor<PageDesign> pageCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).update(pageCaptor.capture(), any());
		assertEquals("夏日首页", pageCaptor.getValue().getPageName());
		assertEquals(2, pageCaptor.getValue().getSchemaVersion());
		assertEquals("{\"schemaVersion\":2,\"components\":[]}", pageCaptor.getValue().getPageContent());
	}

	@Test
	void saveDraftRejectsStaleOrInaccessibleRevision() {
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(0);
		PageDesignDraftDTO draft = new PageDesignDraftDTO();
		draft.setId("page-1");
		draft.setPageContent(JSON.parseObject("{\"components\":[]}"));
		draft.setSchemaVersion(2);
		draft.setDraftRevision(3L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.saveDraft(draft));

		assertEquals("草稿已被其他人修改，请重新加载", error.getMsg());
	}

	@Test
	void getEditorReturnsTypedDraftAndPublishMetadata() {
		PageDesign page = new PageDesign();
		page.setId("page-1");
		page.setPageName("首页");
		page.setPageType("1");
		page.setPageContent("{\"schemaVersion\":2,\"components\":[]}");
		page.setSchemaVersion(2);
		page.setDraftRevision(5L);
		page.setPublishedStatus("1");
		page.setPublishedVersionId("version-3");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);

		PageDesignEditorVO editor = service.getEditor("page-1");

		assertEquals("page-1", editor.getId());
		assertEquals(2, editor.getPageContent().getInteger("schemaVersion"));
		assertEquals(5L, editor.getDraftRevision());
		assertEquals("1", editor.getPublishedStatus());
		assertEquals("version-3", editor.getPublishedVersionId());
	}

	@Test
	void copyPageCreatesUnpublishedMicroPageDraft() {
		PageDesign source = new PageDesign();
		source.setId("home-1");
		source.setPageName("Summer home");
		source.setPageType("1");
		source.setHomeStatus("1");
		source.setStatus("0");
		source.setPageContent("{\"schemaVersion\":2,\"components\":[]}");
		source.setSchemaVersion(2);
		source.setDraftRevision(8L);
		source.setPublishedStatus("1");
		source.setPublishedVersionId("version-3");
		when(pageDesignMapper.selectById("home-1")).thenReturn(source);
		when(pageDesignMapper.insert(any(PageDesign.class))).thenAnswer(invocation -> {
			PageDesign inserted = invocation.getArgument(0);
			inserted.setId("copy-1");
			return 1;
		});

		PageDesign copy = service.copyPage("home-1");

		assertEquals("copy-1", copy.getId());
		assertEquals("Summer home 副本", copy.getPageName());
		assertEquals("0", copy.getPageType());
		assertEquals("0", copy.getHomeStatus());
		assertEquals(0L, copy.getDraftRevision());
		assertEquals("0", copy.getPublishedStatus());
		assertEquals(null, copy.getPublishedVersionId());
		assertEquals(source.getPageContent(), copy.getPageContent());
	}

	private static final class TestPageDesignService extends PageDesignServiceImpl {

		private TestPageDesignService(StringRedisTemplate redisTemplate, RedissonClient redissonClient,
				PageDesignAuditService auditService, PageDesignMapper pageDesignMapper) {
			super(redisTemplate, redissonClient, auditService);
			this.baseMapper = pageDesignMapper;
		}
	}

}
