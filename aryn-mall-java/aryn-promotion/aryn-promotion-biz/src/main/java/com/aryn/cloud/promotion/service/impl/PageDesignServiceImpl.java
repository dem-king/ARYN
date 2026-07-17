
package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.vo.PageDesignEditorVO;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.service.IPageDesignService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 页面设计
 *
 * @author 雨滴kian
 * @date 2022/12/07
 */
@Service
@RequiredArgsConstructor
public class PageDesignServiceImpl extends ServiceImpl<PageDesignMapper, PageDesign> implements IPageDesignService {

	private final StringRedisTemplate redisTemplate;

	private final RedissonClient redissonClient;

	@Override
	public PageDesign getHomePage(PageDesign request) {
		String homePageKey = CacheConstants.HOME_PAGE_DESIGN_CACHE + ArynTenantContextHolder.getTenantId();
		// 查询首页缓存
		String cache = redisTemplate.opsForValue().get(homePageKey);
		if (StringUtils.hasText(cache)) {
			return JSON.parseObject(cache, PageDesign.class);
		}
		// 加锁
		RLock rLock = redissonClient
			.getLock(CacheConstants.HOME_PAGE_DESIGN_LOCK_CACHE + ArynTenantContextHolder.getTenantId());
		boolean locked = false;
		try {
			locked = rLock.tryLock(5, TimeUnit.SECONDS);
			if (!locked) {
				throw new ArynBusinessException("系统繁忙.请刷新重试");
			}
			// 再次检查缓存
			cache = redisTemplate.opsForValue().get(homePageKey);
			if (StringUtils.hasText(cache)) {
				return JSON.parseObject(cache, PageDesign.class);
			}
			// 查询数据库
			PageDesign pageDesign = baseMapper
				.selectOne(Wrappers.query(request).lambda().eq(PageDesign::getPageType, "1").last("limit 1"));
			if (Objects.nonNull(pageDesign)) {
				redisTemplate.opsForValue()
					.set(homePageKey, JSON.toJSONString(pageDesign), 60 * 60 * 24 * 7, TimeUnit.SECONDS);
				return pageDesign;
			}
			else {
				redisTemplate.opsForValue().set(homePageKey, JSON.toJSONString(new PageDesign()), 60, TimeUnit.SECONDS);
				return null;
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("系统繁忙.请刷新重试");
		}
		catch (ArynBusinessException e) {
			throw e;
		}
		catch (Exception e) {
			throw new ArynBusinessException("系统繁忙.请刷新重试");
		}
		finally {
			if (locked) {
				rLock.unlock();
			}
		}
	}

	@Override
	public PageDesign getOrCreateHomePage() {
		RLock lock = redissonClient
			.getLock(CacheConstants.HOME_PAGE_DESIGN_LOCK_CACHE + ArynTenantContextHolder.getTenantId());
		boolean locked = false;
		try {
			locked = lock.tryLock(5, TimeUnit.SECONDS);
			if (!locked) {
				throw new ArynBusinessException("系统繁忙，请刷新重试");
			}
			PageDesign homePage = baseMapper.selectOne(Wrappers.<PageDesign>lambdaQuery()
				.eq(PageDesign::getPageType, "1")
				.last("limit 1"));
			if (Objects.nonNull(homePage)) {
				return homePage;
			}
			homePage = new PageDesign();
			homePage.setPageName("首页");
			homePage.setPageType("1");
			homePage.setHomeStatus(CommonConstants.YES);
			homePage.setStatus("0");
			homePage.setPageContent("{\"components\":[]}");
			baseMapper.insert(homePage);
			evictHomePageCacheAfterCommit();
			return homePage;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("系统繁忙，请刷新重试");
		}
		finally {
			if (locked) {
				lock.unlock();
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updatePageDesignById(PageDesign pageDesign) {
		if (!CommonConstants.YES.equals(pageDesign.getHomeStatus())) {
			return updateAndEvictCache(pageDesign);
		}
		RLock lock = redissonClient
			.getLock(CacheConstants.HOME_PAGE_DESIGN_LOCK_CACHE + ArynTenantContextHolder.getTenantId());
		boolean locked = false;
		try {
			locked = lock.tryLock(5, TimeUnit.SECONDS);
			if (!locked) {
				throw new ArynBusinessException("系统繁忙，请刷新重试");
			}
			PageDesign resetPage = new PageDesign();
			resetPage.setHomeStatus(CommonConstants.NO);
			resetPage.setPageType("0");
			baseMapper.update(resetPage, Wrappers.<PageDesign>lambdaQuery()
				.ne(PageDesign::getId, pageDesign.getId())
				.eq(PageDesign::getHomeStatus, CommonConstants.YES));
			pageDesign.setPageType("1");
			return updateAndEvictCache(pageDesign);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("系统繁忙，请刷新重试");
		}
		finally {
			if (locked) {
				unlockAfterTransactionCompletion(lock);
			}
		}
	}

	private boolean updateAndEvictCache(PageDesign pageDesign) {
		boolean updated = baseMapper.updateById(pageDesign) > 0;
		if (updated) {
			evictHomePageCacheAfterCommit();
		}
		return updated;
	}

	@Override
	public long saveDraft(PageDesignDraftDTO draft) {
		PageDesign update = new PageDesign();
		update.setPageName(draft.getPageName());
		update.setPageContent(JSON.toJSONString(draft.getPageContent()));
		update.setSchemaVersion(draft.getSchemaVersion());
		int updated = baseMapper.update(update, Wrappers.<PageDesign>lambdaUpdate()
			.eq(PageDesign::getId, draft.getId())
			.eq(PageDesign::getDraftRevision, draft.getDraftRevision())
			.setSql("draft_revision = draft_revision + 1"));
		if (updated == 0) {
			throw new ArynBusinessException("草稿已被其他人修改，请重新加载");
		}
		return draft.getDraftRevision() + 1;
	}

	@Override
	public PageDesignEditorVO getEditor(String id) {
		PageDesign pageDesign = baseMapper.selectById(id);
		if (pageDesign == null) {
			throw new ArynBusinessException("页面不存在或无权访问");
		}
		PageDesignEditorVO editor = new PageDesignEditorVO();
		editor.setId(pageDesign.getId());
		editor.setPageName(pageDesign.getPageName());
		editor.setPageType(pageDesign.getPageType());
		editor.setPageContent(JSON.parseObject(pageDesign.getPageContent()));
		editor.setDraftRevision(pageDesign.getDraftRevision());
		editor.setSchemaVersion(pageDesign.getSchemaVersion());
		editor.setPublishedVersionId(pageDesign.getPublishedVersionId());
		editor.setPublishedStatus(pageDesign.getPublishedStatus());
		editor.setPublishedAt(pageDesign.getPublishedAt());
		editor.setUpdateTime(pageDesign.getUpdateTime());
		return editor;
	}

	@Override
	public PageDesign copyPage(String id) {
		PageDesign source = baseMapper.selectById(id);
		if (source == null) {
			throw new ArynBusinessException("页面不存在或无权访问");
		}
		PageDesign copy = new PageDesign();
		copy.setPageName(source.getPageName() + " 副本");
		copy.setPageType("0");
		copy.setHomeStatus(CommonConstants.NO);
		copy.setStatus("0");
		copy.setPageContent(source.getPageContent());
		copy.setSchemaVersion(source.getSchemaVersion());
		copy.setDraftRevision(0L);
		copy.setPublishedStatus("0");
		copy.setLegacyContentBackup(source.getLegacyContentBackup());
		baseMapper.insert(copy);
		return copy;
	}

	private void evictHomePageCacheAfterCommit() {
		String homePageKey = CacheConstants.HOME_PAGE_DESIGN_CACHE + ArynTenantContextHolder.getTenantId();
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			redisTemplate.delete(homePageKey);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				redisTemplate.delete(homePageKey);
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
