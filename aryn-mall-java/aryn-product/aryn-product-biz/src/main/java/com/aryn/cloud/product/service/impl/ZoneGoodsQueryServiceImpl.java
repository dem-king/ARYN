package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.ZoneGoodsIdsVO;
import com.aryn.cloud.product.api.vo.ZoneGoodsRowVO;
import com.aryn.cloud.product.mapper.ZoneGoodsQueryMapper;
import com.aryn.cloud.product.service.IZoneGoodsQueryService;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 专区商品管理查询：分页（固定 id 升序）、按筛选取 ID、SKU 精调计数。
 *
 * <p>生效状态与生效价复用 {@link GoodsZoneTagStateResolver#effective}，与批量预检口径一致
 * （契约 §2.6：OFF_SHELF 优先 → clearance EXPIRED → NO_RULE → INVERTED → NORMAL）。
 *
 * <p>专区默认折扣通过 Dubbo 读取促销域专区设置；读取失败降级为「无默认折扣」，
 * 不阻断列表查询（与 GoodsZoneTagServiceImpl 的降级策略一致）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneGoodsQueryServiceImpl implements IZoneGoodsQueryService {

	private static final Set<String> PAGE_ZONE_TYPES = Set.of("member", "clearance");

	private static final Set<String> IDS_ZONE_TYPES = Set.of("member", "clearance", "none", "not_member",
			"not_clearance");

	private static final long PAGE_DEFAULT_SIZE = 10L;

	private static final long PAGE_MAX_SIZE = 200L;

	private static final int IDS_DEFAULT_LIMIT = 500;

	private static final int IDS_MAX_LIMIT = 1000;

	private final ZoneGoodsQueryMapper zoneGoodsQueryMapper;

	private final GoodsZoneTagStateResolver zoneTagStateResolver;

	/**
	 * 非 final + 字段注入：构造器注入 Dubbo 远程接口曾在 boot 单体启动失败。
	 */
	@DubboReference
	private RemoteMallZoneSettingService remoteMallZoneSettingService;

	@Override
	public IPage<ZoneGoodsRowVO> page(ZoneGoodsQueryDTO query) {
		if (query == null) {
			throw new ArynBusinessException("查询条件不能为空");
		}
		String zoneType = normalizePageZoneType(query.getZoneType());
		long current = query.getCurrent() <= 0 ? 1L : query.getCurrent();
		long size = query.getSize() <= 0 ? PAGE_DEFAULT_SIZE : Math.min(query.getSize(), PAGE_MAX_SIZE);
		IPage<GoodsSpu> spuPage = zoneGoodsQueryMapper.selectZonePage(new Page<>(current, size), query);
		Page<ZoneGoodsRowVO> result = new Page<>(spuPage.getCurrent(), spuPage.getSize(), spuPage.getTotal());
		List<GoodsSpu> spus = spuPage.getRecords();
		if (CollectionUtils.isEmpty(spus)) {
			result.setRecords(new ArrayList<>());
			return result;
		}
		List<String> spuIds = spus.stream().map(GoodsSpu::getId).toList();
		Map<String, List<GoodsSku>> skuMap = loadSkuMap(spuIds);
		BigDecimal zoneDefaultRate = resolveDefaultRate(zoneType);
		List<ZoneGoodsRowVO> rows = new ArrayList<>(spus.size());
		for (GoodsSpu spu : spus) {
			rows.add(toRow(spu, skuMap.getOrDefault(spu.getId(), List.of()), zoneType, zoneDefaultRate));
		}
		result.setRecords(rows);
		return result;
	}

	@Override
	public ZoneGoodsIdsVO ids(ZoneGoodsIdsDTO query) {
		ZoneGoodsIdsDTO condition = query == null ? new ZoneGoodsIdsDTO() : query;
		condition.setZoneType(normalizeIdsZoneType(condition.getZoneType()));
		int limit = condition.getLimit() == null ? IDS_DEFAULT_LIMIT : condition.getLimit();
		if (limit <= 0) {
			limit = IDS_DEFAULT_LIMIT;
		}
		limit = Math.min(limit, IDS_MAX_LIMIT);
		long total = zoneGoodsQueryMapper.countZoneIds(condition);
		List<String> ids = total == 0 ? List.of() : zoneGoodsQueryMapper.selectZoneIds(condition, limit);
		ZoneGoodsIdsVO vo = new ZoneGoodsIdsVO();
		vo.setTotal(total);
		vo.setIds(ids);
		vo.setTruncated(total > ids.size());
		return vo;
	}

	@Override
	public Map<String, Integer> skuOverrideCounts(Collection<String> spuIds) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return Map.of();
		}
		List<String> ids = spuIds.stream().filter(StringUtils::hasText).distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<String, List<GoodsSku>> skuMap = loadSkuMap(ids);
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String spuId : ids) {
			counts.put(spuId, zoneTagStateResolver.skuOverrideCount(skuMap.getOrDefault(spuId, List.of())));
		}
		return counts;
	}

	private Map<String, List<GoodsSku>> loadSkuMap(Collection<String> spuIds) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return Map.of();
		}
		List<GoodsSku> skus = zoneGoodsQueryMapper.selectSkuZoneRows(spuIds);
		if (CollectionUtils.isEmpty(skus)) {
			return Map.of();
		}
		Map<String, List<GoodsSku>> skuMap = new HashMap<>();
		for (GoodsSku sku : skus) {
			skuMap.computeIfAbsent(sku.getSpuId(), key -> new ArrayList<>()).add(sku);
		}
		return skuMap;
	}

	private ZoneGoodsRowVO toRow(GoodsSpu spu, List<GoodsSku> skus, String zoneType, BigDecimal zoneDefaultRate) {
		ZoneGoodsRowVO row = new ZoneGoodsRowVO();
		row.setId(spu.getId());
		row.setName(spu.getName());
		row.setSubTitle(spu.getSubTitle());
		row.setSpuUrls(spu.getSpuUrls());
		row.setCategoryName(spu.getCategoryName());
		row.setBrandName(spu.getBrandName());
		row.setSalesPrice(spu.getSalesPrice());
		row.setOriginalPrice(spu.getOriginalPrice());
		row.setStatus(spu.getStatus());
		row.setStock(spu.getStock());
		row.setIsMemberItem(spu.getIsMemberItem());
		row.setMemberDiscountRate(spu.getMemberDiscountRate());
		row.setMemberPriceFixed(spu.getMemberPriceFixed());
		row.setMemberPriceFloor(spu.getMemberPriceFloor());
		row.setIsClearanceItem(spu.getIsClearanceItem());
		row.setClearancePrice(spu.getClearancePrice());
		row.setClearanceDiscountRate(spu.getClearanceDiscountRate());
		row.setClearanceExpireTime(spu.getClearanceExpireTime());
		row.setSkuOverrideCount(zoneTagStateResolver.skuOverrideCount(skus));
		row.setZoneType(zoneType);
		// 与预检共用同一生效口径；clearance 用 clearance 默认折扣，member 只用 member 默认折扣
		GoodsZoneTagStateResolver.EffectiveZone effective = "member".equals(zoneType)
				? zoneTagStateResolver.effective(spu, zoneType, zoneDefaultRate, null)
				: zoneTagStateResolver.effective(spu, zoneType, null, zoneDefaultRate);
		row.setState(effective.state());
		row.setStateLabel(effective.stateLabel());
		row.setEffectivePrice(effective.effectivePrice());
		return row;
	}

	/**
	 * 专区默认折扣率：专区未配置/查询失败/专区关闭时返回 null（按无默认折扣推算）。
	 */
	private BigDecimal resolveDefaultRate(String zoneType) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId) || remoteMallZoneSettingService == null) {
			return null;
		}
		try {
			MallZoneSettingSnapshotVO setting = remoteMallZoneSettingService.getSetting(tenantId, zoneType);
			if (setting == null || !setting.isEnabled()) {
				return null;
			}
			return setting.getDefaultDiscountRate();
		}
		catch (Exception ex) {
			log.warn("读取专区默认折扣失败 tenant={} zone={}，按无默认折扣推算", tenantId, zoneType, ex);
			return null;
		}
	}

	private String normalizePageZoneType(String zoneType) {
		if (!StringUtils.hasText(zoneType) || !PAGE_ZONE_TYPES.contains(zoneType)) {
			throw new ArynBusinessException("专区类型只支持 member / clearance");
		}
		return zoneType;
	}

	private String normalizeIdsZoneType(String zoneType) {
		if (!StringUtils.hasText(zoneType)) {
			return null;
		}
		if (!IDS_ZONE_TYPES.contains(zoneType)) {
			throw new ArynBusinessException("专区类型只支持 member / clearance / none / not_member / not_clearance");
		}
		return zoneType;
	}

}
