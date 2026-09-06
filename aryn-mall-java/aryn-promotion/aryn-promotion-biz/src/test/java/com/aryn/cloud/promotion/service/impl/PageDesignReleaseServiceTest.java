package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignGrayTargetDTO;
import com.aryn.cloud.promotion.api.dto.PageDesignReleaseAuditDTO;
import com.aryn.cloud.promotion.api.dto.PageDesignReleaseCreateDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignAuditLog;
import com.aryn.cloud.promotion.api.entity.PageDesignRelease;
import com.aryn.cloud.promotion.api.entity.PageDesignReleaseTarget;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignReleaseVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseTargetMapper;
import com.aryn.cloud.promotion.service.IPageDesignThemeService;
import com.aryn.cloud.promotion.service.IPageDesignVersionService;
import com.aryn.cloud.promotion.service.PageDesignAuditService;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import com.aryn.cloud.promotion.service.PageDesignReleaseService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignReleaseServiceTest {

	@Mock
	private PageDesignMapper pageDesignMapper;

	@Mock
	private PageDesignReleaseMapper releaseMapper;

	@Mock
	private PageDesignReleaseTargetMapper releaseTargetMapper;

	@Mock
	private IPageDesignVersionService versionService;

	@Mock
	private PageDesignDocumentValidator validator;

	@Mock
	private IPageDesignThemeService themeService;

	@Mock
	private PageDesignAuditService auditService;

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RLock lock;

	@Mock
	private UserSupplier userSupplier;

	private PageDesignReleaseService service;

	@BeforeAll
	static void initLambdaCache() {
		// 纯 Mockito 环境没有 MyBatis 启动流程，手动初始化 lambda 包装所需的实体列缓存
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
		TableInfoHelper.initTableInfo(assistant, PageDesign.class);
		TableInfoHelper.initTableInfo(assistant, PageDesignRelease.class);
		TableInfoHelper.initTableInfo(assistant, PageDesignReleaseTarget.class);
	}

	@BeforeEach
	void setUp() throws InterruptedException {
		ArynTenantContextHolder.setTenantId("tenant-1");
		when(redissonClient.getLock(anyString())).thenReturn(lock);
		when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
		service = new PageDesignReleaseService(pageDesignMapper, releaseMapper, releaseTargetMapper, versionService,
				validator, themeService, auditService, redissonClient, userSupplier);
		ReflectionTestUtils.setField(service, "approvalRequired", false);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void submitPublishesSnapshotImmediatelyWhenApprovalDisabled() {
		when(userSupplier.getCurrentUserName()).thenReturn("ops-user");
		PageDesign page = page("page-1", 4L);
		PageDesignVersion version = publishedVersion("version-9", 1);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(releaseMapper.selectOne(any())).thenReturn(null);
		when(releaseMapper.insert(any(PageDesignRelease.class))).thenReturn(1);
		when(versionService.publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong()))
			.thenReturn(version);
		PageDesignReleaseCreateDTO request = createRequest(4L);

		PageDesignReleaseVO release = service.submit("page-1", request);

		assertEquals(PageDesignRelease.STATUS_PUBLISHED, release.getReleaseStatus());
		assertEquals(1, release.getReleaseNo());
		assertEquals("ops-user", release.getSubmitBy());
		assertEquals("version-9", release.getReleaseVersionId());
		ArgumentCaptor<PageDesignRelease> releaseCaptor = ArgumentCaptor.forClass(PageDesignRelease.class);
		verify(releaseMapper).insert(releaseCaptor.capture());
		assertEquals("tenant-1", releaseCaptor.getValue().getTenantId());
		assertEquals(page.getPageContent(), releaseCaptor.getValue().getPageContent());
		ArgumentCaptor<PageDesignAuditService.PageDesignAuditEvent> eventCaptor = ArgumentCaptor
			.forClass(PageDesignAuditService.PageDesignAuditEvent.class);
		verify(auditService).record(eventCaptor.capture());
		assertEquals(PageDesignAuditLog.ACTION_RELEASE_SUBMIT, eventCaptor.getValue().action());
		assertEquals("version-9", eventCaptor.getValue().afterVersionId());
	}

	@Test
	void submitStaysPendingWhenApprovalRequired() {
		ReflectionTestUtils.setField(service, "approvalRequired", true);
		when(userSupplier.getCurrentUserName()).thenReturn("ops-user");
		PageDesign page = page("page-1", 4L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(releaseMapper.selectOne(any())).thenReturn(null);
		when(releaseMapper.insert(any(PageDesignRelease.class))).thenReturn(1);
		PageDesignReleaseCreateDTO request = createRequest(4L);

		PageDesignReleaseVO release = service.submit("page-1", request);

		assertEquals(PageDesignRelease.STATUS_PENDING, release.getReleaseStatus());
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
		ArgumentCaptor<PageDesignAuditService.PageDesignAuditEvent> eventCaptor = ArgumentCaptor
			.forClass(PageDesignAuditService.PageDesignAuditEvent.class);
		verify(auditService).record(eventCaptor.capture());
		assertEquals(PageDesignAuditLog.ACTION_RELEASE_SUBMIT, eventCaptor.getValue().action());
	}

	@Test
	void approvePublishesReviewedSnapshot() {
		ReflectionTestUtils.setField(service, "approvalRequired", true);
		when(userSupplier.getCurrentUserName()).thenReturn("reviewer");
		PageDesign page = page("page-1", 4L);
		PageDesignRelease pending = pendingRelease();
		PageDesignVersion version = publishedVersion("version-10", 2);
		when(releaseMapper.selectById("release-1")).thenReturn(pending);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(versionService.publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong()))
			.thenReturn(version);
		PageDesignReleaseAuditDTO request = new PageDesignReleaseAuditDTO();
		request.setApproved(true);
		request.setAuditRemark("通过");

		PageDesignReleaseVO release = service.audit("release-1", request);

		assertEquals(PageDesignRelease.STATUS_PUBLISHED, release.getReleaseStatus());
		assertEquals("version-10", release.getReleaseVersionId());
		assertEquals("reviewer", release.getAuditBy());
		assertEquals("通过", release.getAuditRemark());
		verify(versionService).publishSnapshot(any(PageDesign.class), any(), any(), any(), any(), any(), anyLong());
		ArgumentCaptor<PageDesignAuditService.PageDesignAuditEvent> eventCaptor = ArgumentCaptor
			.forClass(PageDesignAuditService.PageDesignAuditEvent.class);
		verify(auditService).record(eventCaptor.capture());
		assertEquals(PageDesignAuditLog.ACTION_RELEASE_APPROVE, eventCaptor.getValue().action());
		assertEquals("version-10", eventCaptor.getValue().afterVersionId());
	}

	@Test
	void approveRejectsWhenDraftChangedDuringReview() {
		ReflectionTestUtils.setField(service, "approvalRequired", true);
		PageDesign page = page("page-1", 7L);
		PageDesignRelease pending = pendingRelease();
		when(releaseMapper.selectById("release-1")).thenReturn(pending);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		PageDesignReleaseAuditDTO request = new PageDesignReleaseAuditDTO();
		request.setApproved(true);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.audit("release-1", request));

		assertTrue(error.getMsg().contains("审批期间已变化"));
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void rejectMarksReleaseRejectedWithoutPublishing() {
		ReflectionTestUtils.setField(service, "approvalRequired", true);
		when(userSupplier.getCurrentUserName()).thenReturn("reviewer");
		PageDesignRelease pending = pendingRelease();
		when(releaseMapper.selectById("release-1")).thenReturn(pending);
		PageDesignReleaseAuditDTO request = new PageDesignReleaseAuditDTO();
		request.setApproved(false);
		request.setAuditRemark("素材未授权");

		PageDesignReleaseVO release = service.audit("release-1", request);

		assertEquals(PageDesignRelease.STATUS_REJECTED, release.getReleaseStatus());
		assertEquals("素材未授权", release.getAuditRemark());
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
		ArgumentCaptor<PageDesignAuditService.PageDesignAuditEvent> eventCaptor = ArgumentCaptor
			.forClass(PageDesignAuditService.PageDesignAuditEvent.class);
		verify(auditService).record(eventCaptor.capture());
		assertEquals(PageDesignAuditLog.ACTION_RELEASE_REJECT, eventCaptor.getValue().action());
	}

	@Test
	void auditRejectsAlreadyProcessedRelease() {
		PageDesignRelease processed = pendingRelease();
		processed.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
		when(releaseMapper.selectById("release-1")).thenReturn(processed);
		PageDesignReleaseAuditDTO request = new PageDesignReleaseAuditDTO();
		request.setApproved(true);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.audit("release-1", request));

		assertTrue(error.getMsg().contains("已处理"));
	}

	@Test
	void submitRejectsStaleDraftRevision() {
		PageDesign page = page("page-1", 5L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		PageDesignReleaseCreateDTO request = createRequest(4L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("草稿已被其他人修改"));
		verify(releaseMapper, never()).insert(any(PageDesignRelease.class));
	}

	@Test
	void submitBlocksOnValidationErrors() {
		PageDesign page = page("page-1", 4L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of("未知组件类型：flash"));
		PageDesignReleaseCreateDTO request = createRequest(4L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("发布校验失败"));
		assertTrue(error.getMsg().contains("未知组件类型"));
		verify(releaseMapper, never()).insert(any(PageDesignRelease.class));
	}

	@Test
	void submitBlocksWhenReferencedThemeMissing() {
		PageDesign page = page("page-1", 4L);
		page.setPageContent("{\"schemaVersion\":3,\"themeRef\":\"theme-404\",\"sections\":[],\"components\":[]}");
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		doThrow(new ArynBusinessException("页面引用的主题不存在或无权访问，请重新选择主题"))
			.when(themeService)
			.assertThemeUsable(page.getPageContent());
		PageDesignReleaseCreateDTO request = createRequest(4L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("主题不存在"));
		verify(releaseMapper, never()).insert(any(PageDesignRelease.class));
	}

	@Test
	void submitIncrementsReleaseNoInsidePage() {
		PageDesign page = page("page-1", 4L);
		PageDesignRelease latest = pendingRelease();
		latest.setReleaseNo(3);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(releaseMapper.selectOne(any())).thenReturn(latest);
		when(releaseMapper.insert(any(PageDesignRelease.class))).thenReturn(1);
		when(versionService.publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong()))
			.thenReturn(publishedVersion("version-11", 3));
		PageDesignReleaseCreateDTO request = createRequest(4L);

		PageDesignReleaseVO release = service.submit("page-1", request);

		assertEquals(4, release.getReleaseNo());
	}

	@Test
	void scheduledSubmitWaitsForPlannedTimeWhenApprovalDisabled() {
		PageDesign page = page("page-1", 4L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(releaseMapper.selectOne(any())).thenReturn(null);
		when(releaseMapper.insert(any(PageDesignRelease.class))).thenReturn(1);
		when(userSupplier.getCurrentUserName()).thenReturn("ops-user");
		PageDesignReleaseCreateDTO request = createRequest(4L);
		request.setReleaseStrategy(PageDesignRelease.STRATEGY_SCHEDULED);
		request.setPlanPublishAt(java.time.LocalDateTime.now().plusHours(2));

		PageDesignReleaseVO release = service.submit("page-1", request);

		assertEquals(PageDesignRelease.STATUS_WAITING, release.getReleaseStatus());
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void scheduledSubmitRejectsMissingPlanTime() {
		PageDesign page = page("page-1", 4L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		PageDesignReleaseCreateDTO request = createRequest(4L);
		request.setReleaseStrategy(PageDesignRelease.STRATEGY_SCHEDULED);

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("计划发布时间"));
	}

	@Test
	void graySubmitCreatesGrayVersionAndTargets() {
		PageDesign page = page("page-1", 4L);
		PageDesignVersion grayVersion = publishedVersion("version-gray", 5);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		when(releaseMapper.selectOne(any())).thenReturn(null);
		when(releaseMapper.insert(any(PageDesignRelease.class))).thenReturn(1);
		when(versionService.createVersionSnapshot(any(), any(), any(), any(), any(), any())).thenReturn(grayVersion);
		when(pageDesignMapper.update(any(), any())).thenReturn(1);
		when(releaseTargetMapper.delete(any())).thenReturn(1);
		when(releaseTargetMapper.insert(any(PageDesignReleaseTarget.class))).thenReturn(1);
		when(userSupplier.getCurrentUserName()).thenReturn("ops-user");
		PageDesignReleaseCreateDTO request = createRequest(4L);
		request.setReleaseStrategy(PageDesignRelease.STRATEGY_GRAY);
		PageDesignGrayTargetDTO target = new PageDesignGrayTargetDTO();
		target.setTargetTenantId("tenant-gray");
		request.setTargets(List.of(target));

		PageDesignReleaseVO release = service.submit("page-1", request);

		assertEquals(PageDesignRelease.STATUS_PUBLISHED, release.getReleaseStatus());
		assertEquals("version-gray", release.getReleaseVersionId());
		verify(versionService).createVersionSnapshot(any(), any(), any(), any(), any(), any());
		verify(releaseTargetMapper).insert(any(PageDesignReleaseTarget.class));
		// 灰度发布不切换稳定指针
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void graySubmitRejectsMissingTargets() {
		PageDesign page = page("page-1", 4L);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(validator.validate(page.getPageContent())).thenReturn(List.of());
		PageDesignReleaseCreateDTO request = createRequest(4L);
		request.setReleaseStrategy(PageDesignRelease.STRATEGY_GRAY);

		ArynBusinessException error = assertThrows(ArynBusinessException.class,
				() -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("灰度目标"));
	}

	@Test
	void publishScheduledReleasePublishesDueSnapshot() {
		PageDesignRelease waiting = pendingRelease();
		waiting.setReleaseStatus(PageDesignRelease.STATUS_WAITING);
		waiting.setReleaseStrategy(PageDesignRelease.STRATEGY_SCHEDULED);
		PageDesign page = page("page-1", 4L);
		when(releaseMapper.selectById("release-1")).thenReturn(waiting);
		when(pageDesignMapper.selectById("page-1")).thenReturn(page);
		when(pageDesignMapper.update(any(), any())).thenReturn(1);
		when(versionService.publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong()))
			.thenReturn(publishedVersion("version-12", 6));
		when(releaseMapper.updateById(any(PageDesignRelease.class))).thenReturn(1);

		service.publishScheduledRelease(waiting);

		ArgumentCaptor<PageDesignRelease> captor = ArgumentCaptor.forClass(PageDesignRelease.class);
		verify(releaseMapper).updateById(captor.capture());
		assertEquals(PageDesignRelease.STATUS_PUBLISHED, captor.getValue().getReleaseStatus());
	}

	@Test
	void cancelMarksPendingReleaseCancelled() {
		PageDesignRelease pending = pendingRelease();
		when(releaseMapper.selectById("release-1")).thenReturn(pending);
		when(releaseMapper.updateById(any(PageDesignRelease.class))).thenReturn(1);

		PageDesignReleaseVO release = service.cancel("page-1", "release-1");

		assertEquals(PageDesignRelease.STATUS_CANCELLED, release.getReleaseStatus());
		verify(versionService, never()).publishSnapshot(any(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void cancelRejectsProcessedRelease() {
		PageDesignRelease processed = pendingRelease();
		processed.setReleaseStatus(PageDesignRelease.STATUS_REJECTED);
		when(releaseMapper.selectById("release-1")).thenReturn(processed);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.cancel("page-1", "release-1"));

		assertTrue(error.getMsg().contains("待审批"));
	}

	@Test
	void submitRejectsWhenPageInvisibleForTenant() {
		when(pageDesignMapper.selectById("page-1")).thenReturn(null);
		PageDesignReleaseCreateDTO request = createRequest(4L);

		ArynBusinessException error = assertThrows(ArynBusinessException.class, () -> service.submit("page-1", request));

		assertTrue(error.getMsg().contains("页面不存在或无权访问"));
		verify(releaseMapper, never()).insert(any(PageDesignRelease.class));
	}

	private PageDesign page(String id, Long revision) {
		PageDesign page = new PageDesign();
		page.setId(id);
		page.setPageName("大促首页");
		page.setPageType("0");
		page.setPageContent("{\"schemaVersion\":3,\"sections\":[],\"components\":[]}");
		page.setSchemaVersion(3);
		page.setDraftRevision(revision);
		page.setPublishedVersionId("version-8");
		page.setPublishedStatus("1");
		return page;
	}

	private PageDesignRelease pendingRelease() {
		PageDesignRelease release = new PageDesignRelease();
		release.setId("release-1");
		release.setPageDesignId("page-1");
		release.setReleaseNo(1);
		release.setReleaseStrategy(PageDesignRelease.STRATEGY_IMMEDIATE);
		release.setReleaseStatus(PageDesignRelease.STATUS_PENDING);
		release.setDraftRevision(4L);
		release.setSchemaVersion(3);
		release.setPageName("大促首页");
		release.setPageContent("{\"schemaVersion\":3,\"sections\":[],\"components\":[]}");
		return release;
	}

	private PageDesignVersion publishedVersion(String id, int versionNo) {
		PageDesignVersion version = new PageDesignVersion();
		version.setId(id);
		version.setPageDesignId("page-1");
		version.setVersionNo(versionNo);
		version.setSchemaVersion(3);
		return version;
	}

	private PageDesignReleaseCreateDTO createRequest(Long draftRevision) {
		PageDesignReleaseCreateDTO request = new PageDesignReleaseCreateDTO();
		request.setDraftRevision(draftRevision);
		request.setPublishRemark("大促上线");
		return request;
	}

}
