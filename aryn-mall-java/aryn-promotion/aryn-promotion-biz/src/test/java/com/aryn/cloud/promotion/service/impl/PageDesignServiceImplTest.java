package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.vo.PageDesignEditorVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import com.aryn.cloud.promotion.service.PageDesignAuditService;
import com.aryn.cloud.promotion.service.PageDesignPreviewService;
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
import org.springframework.util.StringUtils;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

	@Mock
	private PageDesignPreviewService pageDesignPreviewService;

	@Mock
	private PageDesignVersionMapper pageDesignVersionMapper;

	private PageDesignServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestPageDesignService(redisTemplate, redissonClient, auditService, pageDesignPreviewService,
				pageDesignVersionMapper, pageDesignMapper);
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
	void setAsHomePromotesPublishedMicroPageInsideTenantLock() throws InterruptedException {
		mockAcquiredHomePageLock();
		when(pageDesignMapper.selectById("micro-1")).thenReturn(publishedMicroPage("micro-1"));
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);
		when(pageDesignMapper.updateById(any(PageDesign.class))).thenReturn(1);

		boolean updated = service.setAsHome("micro-1");

		assertTrue(updated);
		ArgumentCaptor<PageDesign> resetCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).update(resetCaptor.capture(), any());
		assertEquals("0", resetCaptor.getValue().getHomeStatus());
		assertEquals("0", resetCaptor.getValue().getPageType());
		ArgumentCaptor<PageDesign> promotedCaptor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).updateById(promotedCaptor.capture());
		assertEquals("micro-1", promotedCaptor.getValue().getId());
		assertEquals("1", promotedCaptor.getValue().getHomeStatus());
		assertEquals("1", promotedCaptor.getValue().getPageType());
		verify(lock).unlock();
	}

	@Test
	void setAsHomeRejectsPageWithoutPublishedVersion() throws InterruptedException {
		mockAcquiredHomePageLock();
		PageDesign draft = new PageDesign();
		draft.setId("draft-1");
		draft.setPageType("0");
		draft.setHomeStatus("0");
		draft.setPublishedStatus("0");
		when(pageDesignMapper.selectById("draft-1")).thenReturn(draft);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.setAsHome("draft-1"));

		assertEquals("页面尚未发布，发布后才能设为首页", error.getMsg());
		verify(pageDesignMapper, never()).update(any(PageDesign.class), any());
		verify(pageDesignMapper, never()).updateById(any(PageDesign.class));
		verify(lock).unlock();
	}

	@Test
	void setAsHomeRejectsMissingPage() throws InterruptedException {
		mockAcquiredHomePageLock();
		when(pageDesignMapper.selectById("missing-1")).thenReturn(null);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.setAsHome("missing-1"));

		assertEquals("页面不存在或无权访问", error.getMsg());
		verify(pageDesignMapper, never()).updateById(any(PageDesign.class));
		verify(lock).unlock();
	}

	@Test
	void setAsHomeRejectsFixedSlotPage() throws InterruptedException {
		mockAcquiredHomePageLock();
		PageDesign categoryPage = publishedMicroPage("category-1");
		categoryPage.setPageType("3");
		when(pageDesignMapper.selectById("category-1")).thenReturn(categoryPage);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.setAsHome("category-1"));

		assertEquals("仅微页面可以设为首页", error.getMsg());
		verify(pageDesignMapper, never()).update(any(PageDesign.class), any());
		verify(pageDesignMapper, never()).updateById(any(PageDesign.class));
		verify(lock).unlock();
	}

	@Test
	void setAsHomeKeepsExistingHomepageWithoutWriting() throws InterruptedException {
		mockAcquiredHomePageLock();
		PageDesign home = publishedMicroPage("home-1");
		home.setHomeStatus("1");
		home.setPageType("1");
		when(pageDesignMapper.selectById("home-1")).thenReturn(home);

		assertTrue(service.setAsHome("home-1"));

		verify(pageDesignMapper, never()).update(any(PageDesign.class), any());
		verify(pageDesignMapper, never()).updateById(any(PageDesign.class));
		verify(lock).unlock();
	}

	@Test
	void setAsHomeEvictsHomepageCacheAfterTransactionCommit() throws InterruptedException {
		mockAcquiredHomePageLock();
		when(pageDesignMapper.selectById("micro-1")).thenReturn(publishedMicroPage("micro-1"));
		when(pageDesignMapper.update(any(PageDesign.class), any())).thenReturn(1);
		when(pageDesignMapper.updateById(any(PageDesign.class))).thenReturn(1);
		TransactionSynchronizationManager.initSynchronization();
		try {
			assertTrue(service.setAsHome("micro-1"));

			verify(redisTemplate, never()).delete(anyString());
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(TransactionSynchronization::afterCommit);
			verify(redisTemplate).delete(anyString());
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void setAsHomeReleasesTenantLockWhenNotAcquired() throws InterruptedException {
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);

		assertThrows(ArynBusinessException.class, () -> service.setAsHome("micro-1"));

		verify(lock, never()).unlock();
	}

	@Test
	void pageMarksCEndEffectiveRowsUsingPreviewRule() {
		PageDesign home = publishedMicroPage("home-1");
		home.setPageType("1");
		home.setHomeStatus("1");
		PageDesign category = publishedMicroPage("cat-1");
		category.setPageType("3");
		Page<PageDesign> result = new Page<>(1, 10);
		result.setRecords(java.util.List.of(home, category));
		when(pageDesignMapper.selectPage(any(Page.class), any())).thenReturn(result);
		PageDesign effectiveCategory = publishedMicroPage("cat-2");
		effectiveCategory.setPageType("3");
		when(pageDesignPreviewService.findEffectivePage("1")).thenReturn(home);
		when(pageDesignPreviewService.findEffectivePage("3")).thenReturn(effectiveCategory);

		Page<PageDesign> returned = service.page(new Page<>(1, 10), Wrappers.emptyWrapper());

		assertSame(result, returned);
		assertEquals(Boolean.TRUE, home.getEffective());
		// 生效分类页不在当前分页内：本页的分类行不得误标为生效
		assertEquals(Boolean.FALSE, category.getEffective());
	}

	/**
	 * 微页面（pageType=0）不是 C 端可枚举的槽位，不存在「生效」语义：
	 * 若一并参与判定，同类型里发布最新的微页面会被误标为「生效中」（列表里表现为
	 * 名字像首页的微页面挂着「生效中」，与真正的「当前首页」并存）。
	 */
	@Test
	void pageNeverMarksMicroPagesAsEffective() {
		PageDesign micro = publishedMicroPage("micro-1");
		Page<PageDesign> result = new Page<>(1, 10);
		result.setRecords(java.util.List.of(micro));
		when(pageDesignMapper.selectPage(any(Page.class), any())).thenReturn(result);

		service.page(new Page<>(1, 10), Wrappers.emptyWrapper());

		assertEquals(Boolean.FALSE, micro.getEffective());
		// 微页面不参与判定：不该产生任何按类型的生效查询
		verify(pageDesignPreviewService, never()).findEffectivePage("0");
	}

	@Test
	void listEffectivePagesReturnsConfiguredSlotsInFixedOrder() {
		PageDesign home = publishedMicroPage("home-1");
		home.setPageType("1");
		home.setPublishedVersionId("version-1");
		home.setPageContent("{\"schemaVersion\":3,\"sections\":[]}");
		PageDesign category = publishedMicroPage("cat-1");
		category.setPageType("3");
		category.setPublishedVersionId("version-2");
		category.setPageContent("{\"draft\":true}");
		when(pageDesignPreviewService.findEffectivePage("1")).thenReturn(home);
		when(pageDesignPreviewService.findEffectivePage("3")).thenReturn(category);
		when(pageDesignPreviewService.findEffectivePage("4")).thenReturn(null);
		when(pageDesignPreviewService.findEffectivePage("2")).thenReturn(null);
		PageDesignVersion homeVersion = new PageDesignVersion();
		homeVersion.setId("version-1");
		homeVersion.setPageContent("{\"published\":true}");
		homeVersion.setSchemaVersion(3);
		when(pageDesignVersionMapper.selectBatchIds(any())).thenReturn(java.util.List.of(homeVersion));

		java.util.List<PageDesign> effectivePages = service.listEffectivePages();

		assertEquals(2, effectivePages.size());
		assertEquals("home-1", effectivePages.get(0).getId());
		assertEquals("cat-1", effectivePages.get(1).getId());
		assertTrue(effectivePages.stream().allMatch(page -> Boolean.TRUE.equals(page.getEffective())));
		// 卡片渲染 C 端实际布局：内容必须是发布版快照，未找到快照的置空降级，不得回退展示草稿
		assertEquals("{\"published\":true}", effectivePages.get(0).getPageContent());
		assertNull(effectivePages.get(1).getPageContent());
	}

	private static PageDesign publishedMicroPage(String id) {
		PageDesign page = new PageDesign();
		page.setId(id);
		page.setPageName("小象超市风格首页");
		page.setPageType("0");
		page.setHomeStatus("0");
		page.setPublishedStatus("1");
		page.setPublishedVersionId("version-2");
		return page;
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
	void createPageFillsNotNullPageContentForBrandNewPage() {
		when(pageDesignMapper.insert(any(PageDesign.class))).thenAnswer(invocation -> {
			PageDesign inserted = invocation.getArgument(0);
			inserted.setId("page-new");
			return 1;
		});
		PageDesign request = new PageDesign();
		request.setPageName("新建页面");
		request.setPageType("0");
		request.setStatus("0");

		PageDesign created = service.createPage(request);

		assertEquals("page-new", created.getId());
		// page_content 为 NOT NULL 且无数据库默认值：必须在插入前显式写入，否则 POST /pagedesign 会 500
		assertTrue(StringUtils.hasText(created.getPageContent()));
		assertEquals(3, created.getSchemaVersion());
		assertEquals(0L, created.getDraftRevision());
		assertEquals("0", created.getPublishedStatus());
		ArgumentCaptor<PageDesign> captor = ArgumentCaptor.forClass(PageDesign.class);
		verify(pageDesignMapper).insert(captor.capture());
		assertTrue(StringUtils.hasText(captor.getValue().getPageContent()));
	}

	@Test
	void createPageKeepsCallerSuppliedContentAndMetadata() {
		when(pageDesignMapper.insert(any(PageDesign.class))).thenReturn(1);
		PageDesign request = new PageDesign();
		request.setPageName("复制页");
		request.setPageContent("{\"schemaVersion\":2,\"components\":[]}");
		request.setSchemaVersion(2);
		request.setDraftRevision(5L);

		PageDesign created = service.createPage(request);

		assertEquals("{\"schemaVersion\":2,\"components\":[]}", created.getPageContent());
		assertEquals(2, created.getSchemaVersion());
		assertEquals(5L, created.getDraftRevision());
	}

	@Test
	void createPageDefaultsBlankPageTypeToMicroPage() {
		when(pageDesignMapper.insert(any(PageDesign.class))).thenReturn(1);
		PageDesign request = new PageDesign();
		request.setPageName("未指定类型");

		PageDesign created = service.createPage(request);

		assertEquals("0", created.getPageType());
	}

	@Test
	void createPageRejectsUnsupportedPageType() {
		PageDesign request = new PageDesign();
		request.setPageName("非法类型");
		// 未被识别的 pageType 会让发布校验查不到组件白名单而放行全部组件，
		// 因此必须在建页时就拒绝，而不是留到发布阶段才发现没有约束。
		request.setPageType("99");

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.createPage(request));

		assertTrue(error.getMsg().contains("不支持的页面类型"));
	}

	@Test
	void createPageRejectsHomePageType() {
		PageDesign request = new PageDesign();
		request.setPageName("想直接建首页");
		// 首页身份由「设为首页」专用流程维护，直接建 pageType=1 会造成双首页
		request.setPageType("1");

		assertThrows(ArynBusinessException.class, () -> service.createPage(request));
	}

	@Test
	void createPageAcceptsAllSupportedPageTypes() {
		when(pageDesignMapper.insert(any(PageDesign.class))).thenReturn(1);
		for (String pageType : java.util.List.of("0", "2", "3", "4")) {
			PageDesign request = new PageDesign();
			request.setPageName("页面-" + pageType);
			request.setPageType(pageType);

			assertEquals(pageType, service.createPage(request).getPageType());
		}
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
				PageDesignAuditService auditService, PageDesignPreviewService pageDesignPreviewService,
				PageDesignVersionMapper pageDesignVersionMapper, PageDesignMapper pageDesignMapper) {
			super(redisTemplate, redissonClient, auditService, pageDesignPreviewService, pageDesignVersionMapper);
			this.baseMapper = pageDesignMapper;
		}
	}

}
