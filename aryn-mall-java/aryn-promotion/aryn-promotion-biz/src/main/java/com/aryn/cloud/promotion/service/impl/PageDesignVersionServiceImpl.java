package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignPublishDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.aryn.cloud.promotion.api.vo.PageDesignVersionVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.mapper.PageDesignVersionMapper;
import com.aryn.cloud.promotion.service.IPageDesignVersionService;
import com.aryn.cloud.promotion.service.PageDesignDocumentValidator;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class PageDesignVersionServiceImpl implements IPageDesignVersionService {

	private static final String PAGE_CACHE_PREFIX = "page_design_cache:";

	private final PageDesignMapper pageDesignMapper;

	private final PageDesignVersionMapper versionMapper;

	private final PageDesignDocumentValidator validator;

	private final StringRedisTemplate redisTemplate;

	private final RedissonClient redissonClient;

	private final UserSupplier userSupplier;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PageDesignVersion publish(PageDesignPublishDTO request) {
		return withPageLock(request.getId(), () -> {
			PageDesign page = requirePage(request.getId());
			if (!Objects.equals(page.getDraftRevision(), request.getDraftRevision())) {
				throw new ArynBusinessException("草稿已被其他人修改，请重新加载");
			}
			List<String> errors = validator.validate(page.getPageContent());
			if (!errors.isEmpty()) {
				throw new ArynBusinessException("发布校验失败：" + String.join("；", errors));
			}
			return createPublishedVersion(page, page.getPageName(), page.getPageType(), page.getPageContent(),
					page.getSchemaVersion(), request.getPublishRemark(), request.getDraftRevision());
		});
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PageDesignVersion rollback(String pageId, String versionId, String publishRemark) {
		return withPageLock(pageId, () -> {
			PageDesign page = requirePage(pageId);
			PageDesignVersion historical = versionMapper.selectById(versionId);
			if (historical == null || !pageId.equals(historical.getPageDesignId())) {
				throw new ArynBusinessException("历史版本不存在或无权访问");
			}
			return createPublishedVersion(page, historical.getPageName(), historical.getPageType(),
					historical.getPageContent(), historical.getSchemaVersion(), publishRemark, null);
		});
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean unpublish(String pageId) {
		return withPageLock(pageId, () -> {
			PageDesign page = requirePage(pageId);
			PageDesign update = new PageDesign();
			update.setPublishedStatus("0");
			boolean updated = pageDesignMapper.update(update,
					Wrappers.<PageDesign>lambdaUpdate().eq(PageDesign::getId, page.getId())) > 0;
			if (updated) {
				evictPageCacheAfterCommit(page);
			}
			return updated;
		});
	}

	@Override
	public List<PageDesignVersionVO> listVersions(String pageId) {
		requirePage(pageId);
		return versionMapper.selectList(Wrappers.<PageDesignVersion>lambdaQuery()
			.eq(PageDesignVersion::getPageDesignId, pageId)
			.orderByDesc(PageDesignVersion::getVersionNo))
			.stream()
			.map(PageDesignVersionVO::from)
			.toList();
	}

	private PageDesignVersion createPublishedVersion(PageDesign page, String pageName, String pageType,
			String pageContent, Integer schemaVersion, String publishRemark, Long expectedDraftRevision) {
		PageDesignVersion latest = versionMapper.selectOne(Wrappers.<PageDesignVersion>lambdaQuery()
			.eq(PageDesignVersion::getPageDesignId, page.getId())
			.orderByDesc(PageDesignVersion::getVersionNo)
			.last("limit 1"));
		LocalDateTime now = LocalDateTime.now();
		PageDesignVersion version = new PageDesignVersion();
		version.setId(IdWorker.getIdStr());
		version.setPageDesignId(page.getId());
		version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
		version.setSchemaVersion(schemaVersion);
		version.setPageName(pageName);
		version.setPageType(pageType);
		version.setPageContent(pageContent);
		version.setPublishRemark(publishRemark);
		version.setPublishBy(userSupplier.getCurrentUserName());
		version.setPublishedAt(now);
		version.setTenantId(ArynTenantContextHolder.getTenantId());
		versionMapper.insert(version);

		PageDesign update = new PageDesign();
		update.setPublishedVersionId(version.getId());
		update.setPublishedStatus("1");
		update.setPublishedAt(now);
		var wrapper = Wrappers.<PageDesign>lambdaUpdate().eq(PageDesign::getId, page.getId());
		if (expectedDraftRevision != null) {
			wrapper.eq(PageDesign::getDraftRevision, expectedDraftRevision);
		}
		if (pageDesignMapper.update(update, wrapper) == 0) {
			throw new ArynBusinessException("页面发布状态已变化，请重新加载");
		}
		evictPageCacheAfterCommit(page);
		return version;
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

	private void evictPageCacheAfterCommit(PageDesign page) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		Runnable eviction = () -> {
			redisTemplate.delete(PAGE_CACHE_PREFIX + tenantId + ":" + page.getId());
			if ("1".equals(page.getPageType())) {
				redisTemplate.delete(CacheConstants.HOME_PAGE_DESIGN_CACHE + tenantId);
			}
		};
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			eviction.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				eviction.run();
			}
		});
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
