package com.aryn.cloud.promotion.dubbo;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.remote.RemoteMallZonePriceService;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.MallZoneActivitySyncServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统出清活动最终价查询（免登录，按租户）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMallZonePriceServiceImpl implements RemoteMallZonePriceService {

	private final PromotionActivityMapper promotionActivityMapper;

	@Override
	public Map<String, BigDecimal> clearancePrices(String tenantId, List<String> skuIds) {
		Map<String, BigDecimal> result = new HashMap<>();
		if (!StringUtils.hasText(tenantId) || skuIds == null || skuIds.isEmpty()) {
			return result;
		}
		String snapshot = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			PromotionActivity activity = promotionActivityMapper.selectOne(Wrappers.<PromotionActivity>lambdaQuery()
				.eq(PromotionActivity::getTenantId, tenantId)
				.eq(PromotionActivity::getActivityCode, MallZoneActivitySyncServiceImpl.SYSTEM_CLEARANCE_ZONE_CODE)
				.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
				.last("LIMIT 1"));
			if (activity == null || !StringUtils.hasText(activity.getRules())) {
				return result;
			}
			Set<String> wanted = new HashSet<>(skuIds);
			LocalDateTime now = LocalDateTime.now();
			JSONArray skus = JSONUtil.parseObj(activity.getRules()).getJSONArray("skus");
			if (skus == null) {
				return result;
			}
			for (Object element : skus) {
				JSONObject sku = (JSONObject) element;
				String skuId = sku.getStr("skuId");
				BigDecimal price = sku.getBigDecimal("price");
				if (!StringUtils.hasText(skuId) || !wanted.contains(skuId) || price == null || price.signum() <= 0) {
					continue;
				}
				String expireTime = sku.getStr("expireTime");
				if (StringUtils.hasText(expireTime)) {
					try {
						if (!LocalDateTime.parse(expireTime).isAfter(now)) {
							continue;
						}
					}
					catch (Exception ex) {
						continue;
					}
				}
				result.put(skuId, price);
			}
			return result;
		}
		catch (Exception ex) {
			log.warn("出清最终价查询失败 tenant={}", tenantId, ex);
			return Map.of();
		}
		finally {
			restoreTenant(snapshot);
		}
	}

	private void restoreTenant(String snapshot) {
		if (snapshot == null) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(snapshot);
		}
	}

}
