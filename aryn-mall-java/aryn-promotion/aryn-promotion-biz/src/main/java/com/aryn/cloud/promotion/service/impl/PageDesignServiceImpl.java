
package com.aryn.cloud.promotion.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.mapper.PageDesignMapper;
import com.aryn.cloud.promotion.service.IPageDesignService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
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
		try {
			if (!rLock.tryLock(5, TimeUnit.SECONDS)) {
				throw new ArynBusinessException("系统繁忙.请刷新重试");
			}
			// 再次检查缓存
			cache = redisTemplate.opsForValue().get(homePageKey);
			if (StringUtils.hasText(cache)) {
				return JSON.parseObject(cache, PageDesign.class);
			}
			// 查询数据库
			PageDesign pageDesign = baseMapper
				.selectOne(Wrappers.query(request).lambda().eq(PageDesign::getHomeStatus, CommonConstants.YES));
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
		catch (Exception e) {
			throw new ArynBusinessException("系统繁忙.请刷新重试");
		}
		finally {
			rLock.unlock();
		}
	}

	@Override
	public boolean updatePageDesignById(PageDesign pageDesign) {
		String homePageKey = CacheConstants.HOME_PAGE_DESIGN_CACHE + ArynTenantContextHolder.getTenantId();
		redisTemplate.delete(homePageKey);
		return baseMapper.updateById(pageDesign) > 0;
	}

}
