package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.core.constant.CacheConstants;
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
import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseMapper;
import com.aryn.cloud.promotion.mapper.PageDesignReleaseTargetMapper;
import com.aryn.cloud.promotion.service.IPageDesignThemeService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 页面装修发布申请服务。
 * <p>
 * 发布流程：草稿保存 -> 服务端校验 -> 创建 release ->（可配置跳过）审批
 * -> 基于申请快照生成不可变版本 -> 更新线上指针 -> 清理缓存 -> 记录审计。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageDesignReleaseService {

	private final PageDesignMapper pageDesignMapper;

	private final PageDesignReleaseMapper releaseMapper;

	private final PageDesignReleaseTargetMapper releaseTargetMapper;

	private final IPageDesignVersionService versionService;

	private final PageDesignDocumentValidator validator;

	private final IPageDesignThemeService themeService;

	private final PageDesignAuditService auditService;

	private final RedissonClient redissonClient;

	private final UserSupplier userSupplier;

	/**
	 * 是否启用发布审批；关闭时提交申请即完成发布，保持既有直发体验。
	 */
	@Value("${decoration.release.approval-required:false}")
	private boolean approvalRequired;

	/**
	 * 发布前结构化校验当前草稿。
	 */
	public PageDesignValidationVO validateDraft(String pageId) {
		PageDesign page = requirePage(pageId);
		return validator.validateStructured(page.getPageContent());
	}

	/**
	 * 提交发布申请：锁定页面、校验草稿修订号与内容、生成快照；
	 * 未开启审批时立即/灰度策略当场执行，定时策略进入待发布状态。
	 */
	@Transactional(rollbackFor = Exception.class)
	public PageDesignReleaseVO submit(String pageId, PageDesignReleaseCreateDTO request) {
		return withPageLock(pageId, () -> {
			PageDesign page = requirePage(pageId);
			if (!Objects.equals(page.getDraftRevision(), request.getDraftRevision())) {
				throw new ArynBusinessException("草稿已被其他人修改，请重新加载");
			}
			List<String> errors = validator.validate(page.getPageContent());
			if (!errors.isEmpty()) {
				throw new ArynBusinessException("发布校验失败：" + String.join("；", errors));
			}
			// 主题引用在提交时即校验，避免审批通过后才发现引用失效
			themeService.assertThemeUsable(page.getPageContent());
			String strategy = StringUtils.hasText(request.getReleaseStrategy())
					? request.getReleaseStrategy() : PageDesignRelease.STRATEGY_IMMEDIATE;
			if (PageDesignRelease.STRATEGY_SCHEDULED.equals(strategy) && request.getPlanPublishAt() == null) {
				throw new ArynBusinessException("定时发布必须指定计划发布时间");
			}
			if (PageDesignRelease.STRATEGY_GRAY.equals(strategy)
					&& (request.getTargets() == null || request.getTargets().isEmpty())) {
				throw new ArynBusinessException("灰度发布必须指定灰度目标租户");
			}
			PageDesignRelease release = new PageDesignRelease();
			release.setId(IdWorker.getIdStr());
			release.setPageDesignId(pageId);
			release.setReleaseNo(nextReleaseNo(pageId));
			release.setReleaseStrategy(strategy);
			release.setReleaseStatus(initialStatus(strategy));
			release.setDraftRevision(request.getDraftRevision());
			release.setSchemaVersion(page.getSchemaVersion());
			release.setPageName(page.getPageName());
			release.setPageContent(page.getPageContent());
			release.setPublishRemark(request.getPublishRemark());
			release.setPlanPublishAt(request.getPlanPublishAt());
			release.setSubmitBy(userSupplier.getCurrentUserName());
			release.setSubmitAt(LocalDateTime.now());
			release.setTenantId(ArynTenantContextHolder.getTenantId());
			releaseMapper.insert(release);
			// 审批开启时灰度目标先落库供审批后使用；自动发布路径由 grayPublish 统一重建目标
			if (approvalRequired && PageDesignRelease.STRATEGY_GRAY.equals(strategy)) {
				insertTargets(release, request.getTargets());
			}

			String afterVersionId = null;
			String auditRemark = "提交发布申请，待审批";
			if (!approvalRequired) {
				afterVersionId = executeRelease(page, release, request.getTargets());
				auditRemark = switch (strategy) {
					case PageDesignRelease.STRATEGY_SCHEDULED -> "提交定时发布申请，等待计划时间";
					case PageDesignRelease.STRATEGY_GRAY -> "提交灰度发布申请并已灰度生效";
					default -> "提交发布申请并自动发布";
				};
			}
			auditService.record(new PageDesignAuditService.PageDesignAuditEvent(
					PageDesignAuditLog.ACTION_RELEASE_SUBMIT, pageId, release.getId(), page.getPublishedVersionId(),
					afterVersionId, request.getDraftRevision(), request.getDraftRevision(), auditRemark));
			return PageDesignReleaseVO.from(release);
		});
	}

	/**
	 * 审批发布申请：通过则按策略发布（立即/灰度）或进入待发布（定时），拒绝则终止申请。
	 */
	@Transactional(rollbackFor = Exception.class)
	public PageDesignReleaseVO audit(String releaseId, PageDesignReleaseAuditDTO request) {
		PageDesignRelease release = releaseMapper.selectById(releaseId);
		if (release == null) {
			throw new ArynBusinessException("发布申请不存在或无权访问");
		}
		return withPageLock(release.getPageDesignId(), () -> {
			PageDesignRelease managed = releaseMapper.selectById(releaseId);
			if (managed == null) {
				throw new ArynBusinessException("发布申请不存在或无权访问");
			}
			if (!PageDesignRelease.STATUS_PENDING.equals(managed.getReleaseStatus())) {
				throw new ArynBusinessException("发布申请已处理，请刷新后查看");
			}
			String action;
			String afterVersionId = null;
			if (Boolean.TRUE.equals(request.getApproved())) {
				PageDesign page = requirePage(managed.getPageDesignId());
				if (!Objects.equals(page.getDraftRevision(), managed.getDraftRevision())) {
					throw new ArynBusinessException("页面草稿在审批期间已变化，请重新提交发布申请");
				}
				if (PageDesignRelease.STRATEGY_GRAY.equals(managed.getReleaseStrategy())) {
					// 灰度目标在提交时已落库，审批通过后重建灰度版本与目标
					PageDesignVersion version = grayPublish(page, managed, listGrayTargets(managed.getId()));
					afterVersionId = version.getId();
					managed.setReleaseVersionId(version.getId());
					managed.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
				}
				else if (PageDesignRelease.STRATEGY_SCHEDULED.equals(managed.getReleaseStrategy())) {
					// 定时策略审批通过后等待计划时间，由调度任务执行
					managed.setReleaseStatus(PageDesignRelease.STATUS_WAITING);
				}
				else {
					PageDesignVersion version = publishRelease(page, managed);
					afterVersionId = version.getId();
					managed.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
					clearGray(managed.getPageDesignId());
				}
				action = PageDesignAuditLog.ACTION_RELEASE_APPROVE;
			}
			else {
				managed.setReleaseStatus(PageDesignRelease.STATUS_REJECTED);
				action = PageDesignAuditLog.ACTION_RELEASE_REJECT;
			}
			managed.setAuditBy(userSupplier.getCurrentUserName());
			managed.setAuditAt(LocalDateTime.now());
			managed.setAuditRemark(request.getAuditRemark());
			releaseMapper.updateById(managed);
			auditService.record(new PageDesignAuditService.PageDesignAuditEvent(action, managed.getPageDesignId(),
					managed.getId(), null, afterVersionId, managed.getDraftRevision(), managed.getDraftRevision(),
					request.getAuditRemark()));
			return PageDesignReleaseVO.from(managed);
		});
	}

	/**
	 * 取消待审批的发布申请。
	 */
	@Transactional(rollbackFor = Exception.class)
	public PageDesignReleaseVO cancel(String pageId, String releaseId) {
		return withPageLock(pageId, () -> {
			PageDesignRelease release = releaseMapper.selectById(releaseId);
			if (release == null || !pageId.equals(release.getPageDesignId())) {
				throw new ArynBusinessException("发布申请不存在或无权访问");
			}
			if (!PageDesignRelease.STATUS_PENDING.equals(release.getReleaseStatus())) {
				throw new ArynBusinessException("仅待审批的发布申请可以取消");
			}
			release.setReleaseStatus(PageDesignRelease.STATUS_CANCELLED);
			release.setAuditAt(LocalDateTime.now());
			releaseMapper.updateById(release);
			auditService.record(new PageDesignAuditService.PageDesignAuditEvent(
					PageDesignAuditLog.ACTION_RELEASE_CANCEL, pageId, release.getId(), null, null, null, null,
					"取消发布申请"));
			return PageDesignReleaseVO.from(release);
		});
	}

	/**
	 * 页面发布申请列表（序号倒序）。
	 */
	public List<PageDesignReleaseVO> listReleases(String pageId) {
		requirePage(pageId);
		return releaseMapper.selectList(Wrappers.<PageDesignRelease>lambdaQuery()
			.eq(PageDesignRelease::getPageDesignId, pageId)
			.orderByDesc(PageDesignRelease::getReleaseNo))
			.stream()
			.map(PageDesignReleaseVO::from)
			.toList();
	}

	private PageDesignVersion publishRelease(PageDesign page, PageDesignRelease release) {
		PageDesignVersion version = versionService.publishSnapshot(page, release.getPageName(), page.getPageType(),
				release.getPageContent(), release.getSchemaVersion(), release.getPublishRemark(),
				release.getDraftRevision());
		release.setReleaseVersionId(version.getId());
		return version;
	}

	/**
	 * 按策略执行发布：立即发布为稳定版；灰度发布覆盖灰度指针；定时发布等待调度。
	 *
	 * @return 产生的发布版本ID（定时策略返回 null）
	 */
	private String executeRelease(PageDesign page, PageDesignRelease release,
			List<PageDesignGrayTargetDTO> requestTargets) {
		return switch (release.getReleaseStrategy()) {
			case PageDesignRelease.STRATEGY_GRAY -> {
				PageDesignVersion version = grayPublish(page, release, requestTargets);
				release.setReleaseVersionId(version.getId());
				release.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
				yield version.getId();
			}
			case PageDesignRelease.STRATEGY_SCHEDULED -> {
				release.setReleaseStatus(PageDesignRelease.STATUS_WAITING);
				yield null;
			}
			default -> {
				PageDesignVersion version = publishRelease(page, release);
				release.setReleaseVersionId(version.getId());
				release.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
				clearGray(page.getId());
				yield version.getId();
			}
		};
	}

	/**
	 * 灰度发布：创建不可变版本、写入灰度指针与目标租户，不影响稳定版本。
	 */
	private PageDesignVersion grayPublish(PageDesign page, PageDesignRelease release,
			List<PageDesignGrayTargetDTO> targets) {
		PageDesignVersion version = versionService.createVersionSnapshot(page, release.getPageName(),
				page.getPageType(), release.getPageContent(), release.getSchemaVersion(), release.getPublishRemark());
		pageDesignMapper.update(null, Wrappers.<PageDesign>lambdaUpdate()
			.eq(PageDesign::getId, page.getId())
			.set(PageDesign::getGrayVersionId, version.getId()));
		// 重建该页面的灰度目标，旧目标随实验结束失效
		releaseTargetMapper.delete(Wrappers.<PageDesignReleaseTarget>lambdaQuery()
			.eq(PageDesignReleaseTarget::getPageDesignId, page.getId()));
		insertTargets(release, targets);
		return version;
	}

	private void insertTargets(PageDesignRelease release, List<PageDesignGrayTargetDTO> targets) {
		if (targets == null) {
			return;
		}
		for (PageDesignGrayTargetDTO target : targets) {
			PageDesignReleaseTarget entity = new PageDesignReleaseTarget();
			entity.setReleaseId(release.getId());
			entity.setPageDesignId(release.getPageDesignId());
			entity.setTargetTenantId(target.getTargetTenantId());
			entity.setTerminal(StringUtils.hasText(target.getTerminal())
					? target.getTerminal() : PageDesignReleaseTarget.TERMINAL_ALL);
			entity.setTenantId(ArynTenantContextHolder.getTenantId());
			releaseTargetMapper.insert(entity);
		}
	}

	private List<PageDesignGrayTargetDTO> listGrayTargets(String releaseId) {
		return releaseTargetMapper.selectList(Wrappers.<PageDesignReleaseTarget>lambdaQuery()
			.eq(PageDesignReleaseTarget::getReleaseId, releaseId))
			.stream()
			.map(target -> {
				PageDesignGrayTargetDTO dto = new PageDesignGrayTargetDTO();
				dto.setTargetTenantId(target.getTargetTenantId());
				dto.setTerminal(target.getTerminal());
				return dto;
			})
			.toList();
	}

	private void clearGray(String pageId) {
		pageDesignMapper.update(null, Wrappers.<PageDesign>lambdaUpdate()
			.eq(PageDesign::getId, pageId)
			.set(PageDesign::getGrayVersionId, ""));
	}

	private String initialStatus(String strategy) {
		if (approvalRequired) {
			return PageDesignRelease.STATUS_PENDING;
		}
		return PageDesignRelease.STRATEGY_SCHEDULED.equals(strategy) ? PageDesignRelease.STATUS_WAITING
				: PageDesignRelease.STATUS_PUBLISHED;
	}

	/**
	 * 发布到期的定时申请；由 XXL-JOB 按租户调度，单条失败不影响其余申请。
	 *
	 * @return 本次成功发布的申请数
	 */
	public int publishDueReleases() {
		List<PageDesignRelease> dueReleases = releaseMapper.selectList(Wrappers.<PageDesignRelease>lambdaQuery()
			.eq(PageDesignRelease::getReleaseStatus, PageDesignRelease.STATUS_WAITING)
			.eq(PageDesignRelease::getReleaseStrategy, PageDesignRelease.STRATEGY_SCHEDULED)
			.isNotNull(PageDesignRelease::getPlanPublishAt)
			.le(PageDesignRelease::getPlanPublishAt, LocalDateTime.now()));
		int published = 0;
		for (PageDesignRelease release : dueReleases) {
			try {
				publishScheduledRelease(release);
				published++;
			}
			catch (RuntimeException exception) {
				log.warn("定时发布申请执行失败 releaseId={} pageId={} reason={}", release.getId(),
						release.getPageDesignId(), exception.getMessage());
			}
		}
		return published;
	}

	@Transactional(rollbackFor = Exception.class)
	public void publishScheduledRelease(PageDesignRelease release) {
		withPageLock(release.getPageDesignId(), () -> {
			PageDesignRelease managed = releaseMapper.selectById(release.getId());
			if (managed == null || !PageDesignRelease.STATUS_WAITING.equals(managed.getReleaseStatus())) {
				return null;
			}
			PageDesign page = requirePage(managed.getPageDesignId());
			PageDesignVersion version = publishRelease(page, managed);
			managed.setReleaseStatus(PageDesignRelease.STATUS_PUBLISHED);
			managed.setAuditAt(LocalDateTime.now());
			releaseMapper.updateById(managed);
			clearGray(managed.getPageDesignId());
			auditService.record(new PageDesignAuditService.PageDesignAuditEvent(PageDesignAuditLog.ACTION_PUBLISH,
					managed.getPageDesignId(), managed.getId(), page.getPublishedVersionId(), version.getId(),
					managed.getDraftRevision(), managed.getDraftRevision(), "定时发布到达计划时间"));
			return version;
		});
	}

	private int nextReleaseNo(String pageId) {
		PageDesignRelease latest = releaseMapper.selectOne(Wrappers.<PageDesignRelease>lambdaQuery()
			.eq(PageDesignRelease::getPageDesignId, pageId)
			.orderByDesc(PageDesignRelease::getReleaseNo)
			.last("limit 1"));
		return latest == null ? 1 : latest.getReleaseNo() + 1;
	}

	private PageDesign requirePage(String pageId) {
		PageDesign page = pageDesignMapper.selectById(pageId);
		if (page == null) {
			throw new ArynBusinessException("页面不存在或无权访问");
		}
		return page;
	}

	private <T> T withPageLock(String pageId, Supplier<T> action) {
		RLock lock = redissonClient.getLock(CacheConstants.HOME_PAGE_DESIGN_LOCK_CACHE
				+ ArynTenantContextHolder.getTenantId() + ":" + pageId);
		boolean locked = false;
		try {
			locked = lock.tryLock(5, TimeUnit.SECONDS);
			if (!locked) {
				throw new ArynBusinessException("页面正在发布，请稍后重试");
			}
			return action.get();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("系统繁忙，请稍后重试");
		}
		finally {
			if (locked) {
				unlockAfterTransactionCompletion(lock);
			}
		}
	}

	private void unlockAfterTransactionCompletion(RLock lock) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			lock.unlock();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				lock.unlock();
			}
		});
	}

}
