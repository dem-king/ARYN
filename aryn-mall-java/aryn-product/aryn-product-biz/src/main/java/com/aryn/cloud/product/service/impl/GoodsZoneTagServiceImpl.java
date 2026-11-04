package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsZoneSkuSnapshotDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.util.ClearancePriceResolver;
import com.aryn.cloud.product.api.util.GoodsCostPriceMasker;
import com.aryn.cloud.product.api.vo.AppClearanceGoodsVO;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.api.vo.GoodsZoneTagResultVO;
import com.aryn.cloud.product.api.vo.GoodsZoneTagVO;
import com.aryn.cloud.product.api.vo.ZoneTagWarningVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.support.GoodsZoneTagRuleValidator;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.remote.RemoteMallZonePriceService;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品专区标签实现。
 *
 * <p>标签落商品域：SPU 是选品与批量操作单位，SKU 是同义覆盖（NULL=继承 SPU）；
 * 会员/出清价格由促销域同步为系统托管活动，商品域不感知会员等级与订单规则。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsZoneTagServiceImpl implements IGoodsZoneTagService {

	/** 专区选品单次上限：一屏商品数远小于该值，只防异常调用放大查询。 */
	private static final int CLEARANCE_GOODS_LIMIT = 100;

	/** 批量查价单次上限：商详/购物车一屏 SKU 数远小于该值，只防请求体放大。 */
	private static final int MAX_PRICE_QUERY_SKUS = 200;

	/** 按 SPU 批量查价单次上限：列表一屏商品数远小于该值，只防请求体放大。 */
	private static final int MAX_PRICE_QUERY_SPUS = 200;

	/**
	 * 出清专区候选中商品上限：需要按 SKU 覆盖算出真实出清价后再排序分页，
	 * 不能直接交给 SQL；500 足以覆盖一个专区的在售商品。
	 */
	private static final int ZONE_CANDIDATE_LIMIT = 500;

	/** 一次批量查最终价的上限：覆盖候选商品的 SKU 总量，避免截断导致展示价与结算价不一致。 */
	private static final long MAX_FINAL_PRICE_SKUS = 5000L;

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsPriceHistoryService goodsPriceHistoryService;

	private final GoodsZoneTagStateResolver stateResolver;

	/**
	 * 专区默认折扣/开关：出清专区选品与打标校验读取。
	 *
	 * <p>非 final + @DubboReference（构造器注入远程接口曾在 boot 启动失败）。
	 */
	@DubboReference
	private RemoteMallZoneSettingService remoteMallZoneSettingService;

	/** 系统活动最终出清价（含时间分层），展示与结算同源。 */
	@DubboReference
	private RemoteMallZonePriceService remoteMallZonePriceService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<GoodsZoneTagResultVO> batchSave(GoodsZoneTagBatchDTO dto) {
		if (dto == null || CollectionUtils.isEmpty(dto.getSpuIds())) {
			throw new ArynBusinessException("请选择商品");
		}
		validateProvidedRules(dto);
		List<GoodsZoneTagResultVO> results = new ArrayList<>();
		boolean memberRuleProvided = dto.getMemberDiscountRate() != null || dto.getMemberPriceFixed() != null;
		boolean clearanceRuleProvided = dto.getClearancePrice() != null || dto.getClearanceDiscountRate() != null
				|| dto.getClearanceExpireTime() != null;
		for (String spuId : dto.getSpuIds()) {
			GoodsSpu spu = StringUtils.hasText(spuId) ? goodsSpuMapper.selectById(spuId) : null;
			if (spu == null) {
				results.add(GoodsZoneTagResultVO.fail(spuId, null, "商品不存在或无权操作"));
				continue;
			}
			try {
				// 批量不接收会员价下限：下限统一由成本兜底/单商品精调维护
				applySpuTags(spu, dto.getMemberTag(), dto.getMemberDiscountRate(), dto.getMemberPriceFixed(),
						null, dto.getClearanceTag(), dto.getClearancePrice(),
						dto.getClearanceDiscountRate(), dto.getClearanceExpireTime(), false, false);
				if (Boolean.FALSE.equals(dto.getMemberTag())) {
					clearSkuMember(spuId, true);
				}
				else if (Boolean.TRUE.equals(dto.getMemberTag())) {
					// SPU 重新打标：SKU 标签回归继承（清掉历史显式 0），但只有勾选覆盖时才清价格精调
					resetSkuMemberTagIfNeeded(spuId);
					if (memberRuleProvided && Boolean.TRUE.equals(dto.getOverwriteSkuRules())) {
						clearSkuMember(spuId, false);
					}
				}
				if (Boolean.FALSE.equals(dto.getClearanceTag())) {
					clearSkuClearance(spuId, true);
				}
				else if (Boolean.TRUE.equals(dto.getClearanceTag())) {
					resetSkuClearanceTagIfNeeded(spuId);
					if (clearanceRuleProvided && Boolean.TRUE.equals(dto.getOverwriteSkuRules())) {
						clearSkuClearance(spuId, false);
					}
				}
				recordSpuTagChanges(spu, dto.getMemberTag() != null || memberRuleProvided,
						Boolean.FALSE.equals(dto.getMemberTag()), dto.getMemberTag(), dto.getMemberDiscountRate(),
						dto.getMemberPriceFixed(), null, dto.getClearanceTag() != null || clearanceRuleProvided,
						Boolean.FALSE.equals(dto.getClearanceTag()), dto.getClearanceTag(),
						dto.getClearancePrice(), dto.getClearanceDiscountRate());
				String message = "OK";
				if (Boolean.TRUE.equals(dto.getClearanceTag())
						&& goodsPriceHistoryService.hasRecentSalesIncrease(spuId, 30)) {
					message = "最近30天售价上调过，请确认出清价真实优惠";
				}
				results.add(new GoodsZoneTagResultVO(spuId, spu.getName(), true, message));
			}
			catch (ArynBusinessException ex) {
				results.add(GoodsZoneTagResultVO.fail(spuId, spu.getName(), ex.getMessage()));
			}
		}
		return results;
	}

	@Override
	public GoodsZoneTagVO getTags(String spuId) {
		GoodsSpu spu = requireSpu(spuId);
		List<GoodsSku> skus = goodsSkuMapper.selectBySpuId(spuId);
		GoodsZoneTagVO vo = new GoodsZoneTagVO();
		vo.setSpuId(spu.getId());
		vo.setSpuName(spu.getName());
		vo.setIsMemberItem(spu.getIsMemberItem());
		vo.setMemberDiscountRate(spu.getMemberDiscountRate());
		vo.setMemberPriceFixed(spu.getMemberPriceFixed());
		vo.setMemberPriceFloor(spu.getMemberPriceFloor());
		vo.setIsClearanceItem(spu.getIsClearanceItem());
		vo.setClearancePrice(spu.getClearancePrice());
		vo.setClearanceDiscountRate(spu.getClearanceDiscountRate());
		vo.setClearanceExpireTime(spu.getClearanceExpireTime());
		vo.setSkus(skus.stream().map(this::toSkuTagVO).toList());
		vo.setSkuOverrideCount(stateResolver.skuOverrideCount(skus));
		vo.setWarnings(buildWarnings(spu));
		return vo;
	}

	/**
	 * 单商品回显告警：缺规则、倒挂、到期、下架、先涨后降。
	 */
	private List<ZoneTagWarningVO> buildWarnings(GoodsSpu spu) {
		List<ZoneTagWarningVO> warnings = new ArrayList<>(
				stateResolver.itemWarnings(spu, goodsPriceHistoryService.hasRecentSalesIncrease(spu.getId(), 30)));
		BigDecimal memberDefault = loadDefaultRate(GoodsZoneTagStateResolver.ZONE_MEMBER);
		BigDecimal clearanceDefault = loadDefaultRate(GoodsZoneTagStateResolver.ZONE_CLEARANCE);
		if (Objects.equals(spu.getIsMemberItem(), 1)) {
			GoodsZoneTagStateResolver.EffectiveZone memberState = stateResolver.effective(spu,
					GoodsZoneTagStateResolver.ZONE_MEMBER, memberDefault, clearanceDefault);
			addStateWarning(warnings, memberState, "会员专区");
		}
		if (Objects.equals(spu.getIsClearanceItem(), 1)) {
			GoodsZoneTagStateResolver.EffectiveZone clearanceState = stateResolver.effective(spu,
					GoodsZoneTagStateResolver.ZONE_CLEARANCE, memberDefault, clearanceDefault);
			addStateWarning(warnings, clearanceState, "低价捡漏");
		}
		return warnings;
	}

	private void addStateWarning(List<ZoneTagWarningVO> warnings, GoodsZoneTagStateResolver.EffectiveZone state,
			String zoneLabel) {
		switch (state.state()) {
			case "NO_RULE" -> warnings.add(GoodsZoneTagStateResolver.warning("NO_RULE",
					zoneLabel + "已打标但未配置可用规则，专区价不会生效"));
			case "INVERTED" -> warnings.add(GoodsZoneTagStateResolver.warning("INVERTED_PRICE",
					zoneLabel + "按当前规则算出的价格不低于售价，专区价不会生效"));
			case "EXPIRED" -> warnings.add(GoodsZoneTagStateResolver.warning("EXPIRED",
					zoneLabel + "已到期，C 端专区不再展示或出价"));
			default -> {
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveTags(String spuId, GoodsZoneTagSaveDTO dto) {
		GoodsSpu spu = requireSpu(spuId);
		GoodsZoneTagRuleValidator.validateSavePayload(dto);
		BigDecimal clearanceDefault = loadDefaultRate(GoodsZoneTagStateResolver.ZONE_CLEARANCE);
		if (Boolean.TRUE.equals(dto.getClearanceTag())) {
			GoodsZoneTagRuleValidator.validateClearanceAvailability(true, dto.getClearancePrice(),
					dto.getClearanceDiscountRate(), spu.getClearancePrice(), spu.getClearanceDiscountRate(),
					clearanceDefault, spu.getOriginalPrice());
		}
		else if (dto.getClearanceDiscountRate() != null
				&& (spu.getOriginalPrice() == null || spu.getOriginalPrice().signum() <= 0)) {
			throw new ArynBusinessException("商品缺少原价，无法按出清折扣算价，请配置出清固定价");
		}
		boolean memberTouched = dto.getMemberTag() != null || dto.getMemberDiscountRate() != null
				|| dto.getMemberPriceFixed() != null || dto.getMemberPriceFloor() != null
				|| Boolean.TRUE.equals(dto.getClearMemberRule());
		boolean clearanceTouched = dto.getClearanceTag() != null || dto.getClearancePrice() != null
				|| dto.getClearanceDiscountRate() != null || dto.getClearanceExpireTime() != null
				|| Boolean.TRUE.equals(dto.getClearClearanceRule());
		applySpuTags(spu, dto.getMemberTag(), dto.getMemberDiscountRate(), dto.getMemberPriceFixed(),
				dto.getMemberPriceFloor(), dto.getClearanceTag(), dto.getClearancePrice(),
				dto.getClearanceDiscountRate(), dto.getClearanceExpireTime(),
				Boolean.TRUE.equals(dto.getClearMemberRule()), Boolean.TRUE.equals(dto.getClearClearanceRule()));
		if (Boolean.FALSE.equals(dto.getMemberTag())) {
			clearSkuMember(spuId, true);
		}
		if (Boolean.FALSE.equals(dto.getClearanceTag())) {
			clearSkuClearance(spuId, true);
		}
		recordSpuTagChanges(spu, memberTouched, Boolean.TRUE.equals(dto.getClearMemberRule()),
				dto.getMemberTag(), dto.getMemberDiscountRate(), dto.getMemberPriceFixed(),
				dto.getMemberPriceFloor(), clearanceTouched,
				Boolean.TRUE.equals(dto.getClearClearanceRule()), dto.getClearanceTag(),
				dto.getClearancePrice(), dto.getClearanceDiscountRate());
		if (CollectionUtils.isEmpty(dto.getSkus())) {
			return;
		}
		Map<String, GoodsSku> skuMap = goodsSkuMapper.selectBySpuId(spuId)
			.stream()
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		boolean anyMemberTag = false;
		boolean anyClearanceTag = false;
		boolean replaceSkuRules = Boolean.TRUE.equals(dto.getReplaceSkuRules());
		for (GoodsZoneTagSaveDTO.SkuTag item : dto.getSkus()) {
			if (item == null || !StringUtils.hasText(item.getSkuId())) {
				continue;
			}
			if (!skuMap.containsKey(item.getSkuId())) {
				throw new ArynBusinessException("SKU不属于当前商品：" + item.getSkuId());
			}
			if (item.getMemberDiscountRate() != null) {
				validateRate(item.getMemberDiscountRate(), "会员折扣率");
			}
			if (item.getClearanceDiscountRate() != null) {
				validateRate(item.getClearanceDiscountRate(), "出清折扣率");
			}
			validatePositive(item.getMemberPriceFixed(), "会员固定价");
			validatePositive(item.getMemberPriceFloor(), "会员价下限");
			validatePositive(item.getClearancePrice(), "出清价");
			if (item.getClearanceExpireTime() != null && item.getClearanceExpireTime().isBefore(LocalDateTime.now())) {
				throw new ArynBusinessException("出清截止时间不能早于当前时间");
			}
			// SPU 已统一取消标签时，SKU 不允许再单独打标
			Integer memberTag = Boolean.FALSE.equals(dto.getMemberTag()) ? Integer.valueOf(0) : item.getMemberTag();
			Integer clearanceTag = Boolean.FALSE.equals(dto.getClearanceTag()) ? Integer.valueOf(0)
					: item.getClearanceTag();
			anyMemberTag = anyMemberTag || Objects.equals(memberTag, 1);
			anyClearanceTag = anyClearanceTag || Objects.equals(clearanceTag, 1);
			recordSkuTagChanges(spuId, skuMap.get(item.getSkuId()), item, memberTag, clearanceTag);
			var skuUpdate = Wrappers.<GoodsSku>lambdaUpdate()
				.eq(GoodsSku::getId, item.getSkuId())
				.eq(GoodsSku::getSpuId, spuId);
			if (replaceSkuRules) {
				// 精确覆盖：提交值为 null 表示清空该字段并恢复继承 SPU
				skuUpdate.set(GoodsSku::getIsMemberItem, memberTag)
					.set(GoodsSku::getMemberDiscountRate, item.getMemberDiscountRate())
					.set(GoodsSku::getMemberPriceFixed, item.getMemberPriceFixed())
					.set(GoodsSku::getMemberPriceFloor, item.getMemberPriceFloor())
					.set(GoodsSku::getIsClearanceItem, clearanceTag)
					.set(GoodsSku::getClearancePrice, item.getClearancePrice())
					.set(GoodsSku::getClearanceDiscountRate, item.getClearanceDiscountRate())
					.set(GoodsSku::getClearanceExpireTime, item.getClearanceExpireTime());
			}
			else {
				skuUpdate
					.set(memberTag != null, GoodsSku::getIsMemberItem, memberTag)
					.set(item.getMemberDiscountRate() != null, GoodsSku::getMemberDiscountRate,
							item.getMemberDiscountRate())
					.set(item.getMemberPriceFixed() != null, GoodsSku::getMemberPriceFixed,
							item.getMemberPriceFixed())
					.set(item.getMemberPriceFloor() != null, GoodsSku::getMemberPriceFloor,
							item.getMemberPriceFloor())
					.set(clearanceTag != null, GoodsSku::getIsClearanceItem, clearanceTag)
					.set(item.getClearancePrice() != null, GoodsSku::getClearancePrice, item.getClearancePrice())
					.set(item.getClearanceDiscountRate() != null, GoodsSku::getClearanceDiscountRate,
							item.getClearanceDiscountRate())
					.set(item.getClearanceExpireTime() != null, GoodsSku::getClearanceExpireTime,
							item.getClearanceExpireTime());
			}
			goodsSkuMapper.update(null, skuUpdate);
		}
		// SKU 单独打标时同步 SPU 选品标签，否则专区列表（按 SPU 选品）看不到该商品
		if (anyMemberTag && !Objects.equals(spu.getIsMemberItem(), 1)) {
			goodsSpuMapper.update(null, Wrappers.<GoodsSpu>lambdaUpdate()
				.eq(GoodsSpu::getId, spuId)
				.set(GoodsSpu::getIsMemberItem, 1));
		}
		if (anyClearanceTag && !Objects.equals(spu.getIsClearanceItem(), 1)) {
			goodsSpuMapper.update(null, Wrappers.<GoodsSpu>lambdaUpdate()
				.eq(GoodsSpu::getId, spuId)
				.set(GoodsSpu::getIsClearanceItem, 1));
		}
	}

	@Override
	public List<GoodsZoneSkuSnapshotDTO> listZoneSkus() {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return List.of();
		}
		List<GoodsSpu> spus = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getTenantId, tenantId)
			.eq(GoodsSpu::getStatus, "1")
			.and(wrapper -> wrapper.eq(GoodsSpu::getIsMemberItem, 1).or().eq(GoodsSpu::getIsClearanceItem, 1)));
		if (spus.isEmpty()) {
			return List.of();
		}
		Map<String, GoodsSpu> spuMap = spus.stream()
			.collect(Collectors.toMap(GoodsSpu::getId, spu -> spu, (a, b) -> a));
		List<GoodsSku> skus = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getTenantId, tenantId)
			.eq(GoodsSku::getStatus, "0")
			.in(GoodsSku::getSpuId, spuMap.keySet()));
		List<GoodsZoneSkuSnapshotDTO> snapshots = new ArrayList<>();
		for (GoodsSku sku : skus) {
			GoodsSpu spu = spuMap.get(sku.getSpuId());
			if (spu == null) {
				continue;
			}
			GoodsZoneSkuSnapshotDTO snapshot = toSnapshot(sku, spu);
			if (Boolean.TRUE.equals(snapshot.getMemberItem()) || Boolean.TRUE.equals(snapshot.getClearanceItem())) {
				snapshots.add(snapshot);
			}
		}
		return snapshots;
	}

	@Override
	public AppClearanceGoodsVO clearanceGoods(long limit) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return new AppClearanceGoodsVO();
		}
		long size = limit <= 0 ? CLEARANCE_GOODS_LIMIT : Math.min(limit, CLEARANCE_GOODS_LIMIT);
		LocalDateTime now = LocalDateTime.now();
		MallZoneSettingSnapshotVO setting = loadZoneSetting(tenantId, MallZoneSetting.ZONE_CLEARANCE);
		if (!setting.isEnabled()) {
			AppClearanceGoodsVO vo = new AppClearanceGoodsVO();
			vo.setTotal(0L);
			vo.setItems(List.of());
			applyZoneSetting(vo, setting);
			return vo;
		}
		List<GoodsSpu> spus = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getTenantId, tenantId)
			.eq(GoodsSpu::getStatus, "1")
			.eq(GoodsSpu::getIsClearanceItem, 1)
			.and(wrapper -> wrapper.isNull(GoodsSpu::getClearanceExpireTime)
				.or()
				.gt(GoodsSpu::getClearanceExpireTime, now)));
		Map<String, List<GoodsSku>> skuMap = spus.isEmpty() ? Map.of()
				: goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
					.eq(GoodsSku::getTenantId, tenantId)
					.eq(GoodsSku::getStatus, "0")
					.in(GoodsSku::getSpuId, spus.stream().map(GoodsSpu::getId).toList()))
					.stream()
					.collect(Collectors.groupingBy(GoodsSku::getSpuId));
		Map<String, BigDecimal> finalPrices = loadFinalClearancePrices(tenantId, skuMap);
		List<AppClearanceGoodsVO.Item> items = new ArrayList<>();
		for (GoodsSpu spu : spus) {
			BigDecimal clearancePrice = ClearancePriceResolver.effectiveSpuPrice(spu, skuMap.get(spu.getId()), now);
			BigDecimal finalPrice = finalSpuPrice(spu, skuMap.get(spu.getId()), finalPrices);
			if (finalPrice != null) {
				clearancePrice = finalPrice;
			}
			// 倒挂/无有效价：不进入专区（避免「先涨后降」制造假优惠）
			if (clearancePrice == null || spu.getSalesPrice() == null
					|| clearancePrice.compareTo(spu.getSalesPrice()) >= 0) {
				continue;
			}
			AppClearanceGoodsVO.Item item = new AppClearanceGoodsVO.Item();
			item.setSpuId(spu.getId());
			item.setName(spu.getName());
			item.setSubTitle(spu.getSubTitle());
			item.setPicUrl(spu.getSpuUrls() != null && spu.getSpuUrls().length > 0 ? spu.getSpuUrls()[0] : null);
			item.setSalesPrice(spu.getSalesPrice());
			item.setOriginalPrice(spu.getOriginalPrice());
			item.setClearancePrice(clearancePrice);
			item.setClearanceExpireTime(spu.getClearanceExpireTime());
			items.add(item);
		}
		if ("discount_desc".equals(setting.getSortMode())) {
			// 折扣力度 = (售价 - 出清价) / 售价，降序；同力度按出清价升序
			items.sort(Comparator.comparing((AppClearanceGoodsVO.Item item) -> discountRatio(item))
				.reversed()
				.thenComparing(AppClearanceGoodsVO.Item::getClearancePrice)
				.thenComparing(AppClearanceGoodsVO.Item::getSpuId, Comparator.reverseOrder()));
		}
		else {
			items.sort(Comparator.comparing(AppClearanceGoodsVO.Item::getClearancePrice)
				.thenComparing(AppClearanceGoodsVO.Item::getSalesPrice,
					Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AppClearanceGoodsVO.Item::getSpuId, Comparator.reverseOrder()));
		}
		AppClearanceGoodsVO vo = new AppClearanceGoodsVO();
		vo.setTotal((long) items.size());
		vo.setItems(items.stream().limit(size).toList());
		applyZoneSetting(vo, setting);
		return vo;
	}

	private MallZoneSettingSnapshotVO loadZoneSetting(String tenantId, String zoneType) {
		try {
			MallZoneSettingSnapshotVO setting = remoteMallZoneSettingService.getSetting(tenantId, zoneType);
			if (setting != null) {
				return setting;
			}
		}
		catch (Exception ex) {
			log.warn("专区设置查询失败，按默认开启处理 tenant={} zone={}", tenantId, zoneType, ex);
		}
		MallZoneSettingSnapshotVO fallback = new MallZoneSettingSnapshotVO();
		fallback.setZoneType(zoneType);
		fallback.setEnabled(true);
		fallback.setSortMode("default");
		return fallback;
	}

	private void applyZoneSetting(AppClearanceGoodsVO vo, MallZoneSettingSnapshotVO setting) {
		vo.setSortMode(setting.getSortMode());
	}

	/** 系统活动最终价（含分层）批量查询；失败降级为空，不影响基础价展示。 */
	private Map<String, BigDecimal> loadFinalClearancePrices(String tenantId,
			Map<String, List<GoodsSku>> skuMap) {
		List<String> skuIds = skuMap.values()
			.stream()
			.flatMap(List::stream)
			.map(GoodsSku::getId)
			.filter(StringUtils::hasText)
			.limit(MAX_FINAL_PRICE_SKUS)
			.toList();
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			return remoteMallZonePriceService.clearancePrices(tenantId, skuIds);
		}
		catch (Exception ex) {
			log.warn("出清最终价查询失败 tenant={}", tenantId, ex);
			return Map.of();
		}
	}

	private BigDecimal discountRatio(AppClearanceGoodsVO.Item item) {
		BigDecimal sales = item.getSalesPrice();
		BigDecimal clearance = item.getClearancePrice();
		if (sales == null || sales.signum() <= 0 || clearance == null) {
			return BigDecimal.ZERO;
		}
		return sales.subtract(clearance).divide(sales, 6, java.math.RoundingMode.HALF_UP);
	}

	private BigDecimal finalSpuPrice(GoodsSpu spu, List<GoodsSku> skus, Map<String, BigDecimal> finalPrices) {
		if (skus == null || finalPrices.isEmpty() || spu.getSalesPrice() == null) {
			return null;
		}
		BigDecimal best = null;
		for (GoodsSku sku : skus) {
			BigDecimal price = finalPrices.get(sku.getId());
			BigDecimal skuSales = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
			if (price == null || skuSales == null || skuSales.compareTo(spu.getSalesPrice()) > 0) {
				continue;
			}
			if (price.compareTo(spu.getSalesPrice()) >= 0) {
				continue;
			}
			if (best == null || price.compareTo(best) < 0) {
				best = price;
			}
		}
		return best;
	}

	@Override
	public AppClearancePriceVO clearancePrices(List<String> skuIds) {
		AppClearancePriceVO vo = new AppClearancePriceVO();
		vo.setItems(List.of());
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (CollectionUtils.isEmpty(skuIds) || !StringUtils.hasText(tenantId)) {
			return vo;
		}
		Set<String> wanted = new java.util.LinkedHashSet<>();
		for (String skuId : skuIds) {
			if (StringUtils.hasText(skuId) && wanted.size() < MAX_PRICE_QUERY_SKUS) {
				wanted.add(skuId.trim());
			}
		}
		if (wanted.isEmpty()) {
			return vo;
		}
		MallZoneSettingSnapshotVO setting = loadZoneSetting(tenantId, MallZoneSetting.ZONE_CLEARANCE);
		if (!setting.isEnabled()) {
			return vo;
		}
		List<GoodsSku> skus = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getTenantId, tenantId)
			.eq(GoodsSku::getStatus, "0")
			.in(GoodsSku::getId, wanted));
		if (skus.isEmpty()) {
			return vo;
		}
		Set<String> spuIds = skus.stream()
			.map(GoodsSku::getSpuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());
		Map<String, GoodsSpu> spuMap = spuIds.isEmpty() ? Map.of()
				: goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
					.eq(GoodsSpu::getTenantId, tenantId)
					.eq(GoodsSpu::getStatus, "1")
					.in(GoodsSpu::getId, spuIds))
					.stream()
					.collect(Collectors.toMap(GoodsSpu::getId, spu -> spu, (a, b) -> a));
		LocalDateTime now = LocalDateTime.now();
		Map<String, GoodsSku> skuMap = skus.stream()
			.collect(Collectors.toMap(GoodsSku::getId, sku -> sku, (a, b) -> a));
		Map<String, BigDecimal> finalPrices;
		try {
			finalPrices = remoteMallZonePriceService.clearancePrices(tenantId, new ArrayList<>(wanted));
		}
		catch (Exception ex) {
			log.warn("出清最终价查询失败 tenant={}", tenantId, ex);
			finalPrices = Map.of();
		}
		List<AppClearancePriceVO.Item> items = new ArrayList<>();
		for (String skuId : wanted) {
			GoodsSku sku = skuMap.get(skuId);
			GoodsSpu spu = sku == null ? null : spuMap.get(sku.getSpuId());
			BigDecimal price = ClearancePriceResolver.effectiveUnitPrice(sku, spu, now);
			BigDecimal finalPrice = finalPrices.get(skuId);
			if (finalPrice != null) {
				price = finalPrice;
			}
			if (price == null || sku == null || spu == null) {
				continue;
			}
			BigDecimal salesPrice = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
			if (salesPrice == null || price.compareTo(salesPrice) >= 0) {
				continue;
			}
			AppClearancePriceVO.Item item = new AppClearancePriceVO.Item();
			item.setSkuId(skuId);
			item.setSalesPrice(salesPrice);
			item.setClearancePrice(price);
			item.setClearanceExpireTime(sku.getClearanceExpireTime() != null ? sku.getClearanceExpireTime()
					: spu.getClearanceExpireTime());
			items.add(item);
		}
		vo.setItems(items);
		return vo;
	}

	/**
	 * 按 SPU 批量查有效出清价（商品列表卡片）：每个有效 SKU 一条，由 C 端按卡片价聚合最低价。
	 *
	 * <p>与 {@link #clearancePrices(List)} / 专区列表同口径：基础价走 {@link ClearancePriceResolver}
	 * （SKU 覆盖优先、到期与倒挂过滤），系统活动最终价存在时直接覆盖（含时间分层，展示与结算同源）；
	 * 最终价查询异常降级为空，不阻断基础价展示。未打标、已到期、倒挂的 SKU 不返回。
	 *
	 * <p>这里不按 SPU 的 is_clearance_item 预过滤：SKU 级标签可显式覆盖 SPU（1/0），
	 * 与按 skuIds 查询共用 {@link ClearancePriceResolver#effectiveUnitPrice} 的判定，
	 * 避免同一商品换一种入参得到不同价格。
	 */
	@Override
	public AppClearancePriceVO clearanceSpuPrices(List<String> spuIds) {
		AppClearancePriceVO vo = new AppClearancePriceVO();
		vo.setItems(List.of());
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (CollectionUtils.isEmpty(spuIds) || !StringUtils.hasText(tenantId)) {
			return vo;
		}
		Set<String> wanted = new java.util.LinkedHashSet<>();
		for (String spuId : spuIds) {
			if (StringUtils.hasText(spuId) && wanted.size() < MAX_PRICE_QUERY_SPUS) {
				wanted.add(spuId.trim());
			}
		}
		if (wanted.isEmpty()) {
			return vo;
		}
		MallZoneSettingSnapshotVO setting = loadZoneSetting(tenantId, MallZoneSetting.ZONE_CLEARANCE);
		if (!setting.isEnabled()) {
			return vo;
		}
		Map<String, GoodsSpu> spuMap = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getTenantId, tenantId)
			.eq(GoodsSpu::getStatus, "1")
			.in(GoodsSpu::getId, wanted))
			.stream()
			.collect(Collectors.toMap(GoodsSpu::getId, spu -> spu, (a, b) -> a));
		if (spuMap.isEmpty()) {
			return vo;
		}
		Map<String, List<GoodsSku>> skuMap = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getTenantId, tenantId)
			.eq(GoodsSku::getStatus, "0")
			.in(GoodsSku::getSpuId, spuMap.keySet()))
			.stream()
			.collect(Collectors.groupingBy(GoodsSku::getSpuId));
		if (skuMap.isEmpty()) {
			return vo;
		}
		Map<String, BigDecimal> finalPrices = loadFinalClearancePrices(tenantId, skuMap);
		LocalDateTime now = LocalDateTime.now();
		List<AppClearancePriceVO.Item> items = new ArrayList<>();
		// 输出顺序稳定：SPU 按请求顺序，SPU 内 SKU 按 id 升序（便于前端缓存与测试断言）
		for (String spuId : wanted) {
			GoodsSpu spu = spuMap.get(spuId);
			List<GoodsSku> skus = skuMap.get(spuId);
			if (spu == null || CollectionUtils.isEmpty(skus)) {
				continue;
			}
			List<GoodsSku> orderedSkus = skus.stream()
				.sorted(Comparator.comparing(GoodsSku::getId,
						Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
			for (GoodsSku sku : orderedSkus) {
				BigDecimal price = ClearancePriceResolver.effectiveUnitPrice(sku, spu, now);
				BigDecimal finalPrice = finalPrices.get(sku.getId());
				if (finalPrice != null) {
					price = finalPrice;
				}
				BigDecimal salesPrice = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
				// 未打标/到期/倒挂（含最终价倒挂）：不返回，前端未命中即按原价展示
				if (price == null || salesPrice == null || price.compareTo(salesPrice) >= 0) {
					continue;
				}
				AppClearancePriceVO.Item item = new AppClearancePriceVO.Item();
				item.setSkuId(sku.getId());
				item.setSpuId(spuId);
				item.setSalesPrice(salesPrice);
				item.setClearancePrice(price);
				item.setClearanceExpireTime(sku.getClearanceExpireTime() != null ? sku.getClearanceExpireTime()
						: spu.getClearanceExpireTime());
				items.add(item);
			}
		}
		vo.setItems(items);
		return vo;
	}

	@Override
	public boolean zoneEnabled(String zoneType) {
		return loadZoneSetting(ArynTenantContextHolder.getTenantId(), zoneType).isEnabled();
	}

	/**
	 * 出清专区分页（C 端商品列表/装修 Tab 主入口）。
	 *
	 * <p>与 {@link #clearanceGoods} 同源：先按标签与分类筛选候选，再取系统活动最终价
	 * （含时间分层），最后按专区设置的 sortMode 排序。页面价 = 结算价，专区关闭时返回空页。
	 */
	@Override
	public IPage<GoodsSpu> clearancePage(Page page, GoodsSpu query, Boolean memberItem) {
		long current = page == null || page.getCurrent() <= 0 ? 1 : page.getCurrent();
		long size = page == null || page.getSize() <= 0 ? 10 : page.getSize();
		GoodsSpu filters = query == null ? new GoodsSpu() : query;
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return emptyPage(current, size);
		}
		MallZoneSettingSnapshotVO setting = loadZoneSetting(tenantId, MallZoneSetting.ZONE_CLEARANCE);
		if (!setting.isEnabled()) {
			return emptyPage(current, size);
		}
		String name = StringUtils.hasText(filters.getName()) ? escapeLikeKeyword(filters.getName()) : null;
		List<GoodsSpu> candidates = goodsSpuMapper.selectList(Wrappers.<GoodsSpu>lambdaQuery()
			.eq(GoodsSpu::getTenantId, tenantId)
			.eq(GoodsSpu::getStatus, "1")
			.eq(GoodsSpu::getIsClearanceItem, 1)
			.eq(Boolean.TRUE.equals(memberItem), GoodsSpu::getIsMemberItem, 1)
			.like(StringUtils.hasText(name), GoodsSpu::getName, name)
			.eq(StringUtils.hasText(filters.getCategoryFirstId()), GoodsSpu::getCategoryFirstId,
					filters.getCategoryFirstId())
			.eq(StringUtils.hasText(filters.getCategorySecondId()), GoodsSpu::getCategorySecondId,
					filters.getCategorySecondId())
			.eq(StringUtils.hasText(filters.getBrandId()), GoodsSpu::getBrandId, filters.getBrandId())
			.last("LIMIT " + ZONE_CANDIDATE_LIMIT));
		if (candidates.isEmpty()) {
			return emptyPage(current, size);
		}
		Map<String, List<GoodsSku>> skuMap = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getTenantId, tenantId)
			.eq(GoodsSku::getStatus, "0")
			.in(GoodsSku::getSpuId, candidates.stream().map(GoodsSpu::getId).toList()))
			.stream()
			.collect(Collectors.groupingBy(GoodsSku::getSpuId));
		Map<String, BigDecimal> finalPrices = loadFinalClearancePrices(tenantId, skuMap);
		LocalDateTime now = LocalDateTime.now();
		List<GoodsSpu> valid = new ArrayList<>();
		for (GoodsSpu spu : candidates) {
			BigDecimal price = ClearancePriceResolver.effectiveSpuPrice(spu, skuMap.get(spu.getId()), now);
			BigDecimal finalPrice = finalSpuPrice(spu, skuMap.get(spu.getId()), finalPrices);
			if (finalPrice != null) {
				price = finalPrice;
			}
			if (price == null || spu.getSalesPrice() == null || price.compareTo(spu.getSalesPrice()) >= 0) {
				continue;
			}
			spu.setClearancePrice(price);
			valid.add(spu);
		}
		if ("discount_desc".equals(setting.getSortMode())) {
			valid.sort(Comparator.comparing((GoodsSpu spu) -> spuDiscountRatio(spu))
				.reversed()
				.thenComparing(GoodsSpu::getClearancePrice)
				.thenComparing(GoodsSpu::getId, Comparator.reverseOrder()));
		}
		else {
			valid.sort(Comparator.comparing(GoodsSpu::getClearancePrice)
				.thenComparing(GoodsSpu::getSalesPrice, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(GoodsSpu::getId, Comparator.reverseOrder()));
		}
		int from = (int) Math.min((current - 1) * size, valid.size());
		int to = (int) Math.min(from + size, valid.size());
		Page<GoodsSpu> result = new Page<>(current, size);
		result.setTotal(valid.size());
		result.setRecords(new ArrayList<>(valid.subList(from, to)));
		result.getRecords().forEach(GoodsCostPriceMasker::maskSpu);
		return result;
	}

	private IPage<GoodsSpu> emptyPage(long current, long size) {
		Page<GoodsSpu> result = new Page<>(current, size);
		result.setTotal(0);
		result.setRecords(List.of());
		return result;
	}

	private BigDecimal spuDiscountRatio(GoodsSpu spu) {
		if (spu.getSalesPrice() == null || spu.getSalesPrice().signum() <= 0 || spu.getClearancePrice() == null) {
			return BigDecimal.ZERO;
		}
		return spu.getSalesPrice().subtract(spu.getClearancePrice())
			.divide(spu.getSalesPrice(), 6, java.math.RoundingMode.HALF_UP);
	}

	private String escapeLikeKeyword(String keyword) {
		return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private GoodsZoneSkuSnapshotDTO toSnapshot(GoodsSku sku, GoodsSpu spu) {
		GoodsZoneSkuSnapshotDTO snapshot = new GoodsZoneSkuSnapshotDTO();
		snapshot.setSkuId(sku.getId());
		snapshot.setSpuId(spu.getId());
		snapshot.setSpuName(spu.getName());
		snapshot.setSalesPrice(sku.getSalesPrice());
		snapshot.setOriginalPrice(sku.getOriginalPrice());
		// SKU 成本优先，空则回落 SPU 聚合成本（均为内部字段，只用于促销域算价）
		snapshot.setCostPrice(firstNonNull(sku.getCostPrice(), spu.getCostPrice()));
		snapshot.setMemberItem(resolveTag(sku.getIsMemberItem(), spu.getIsMemberItem()));
		snapshot.setMemberDiscountRate(firstNonNull(sku.getMemberDiscountRate(), spu.getMemberDiscountRate()));
		snapshot.setMemberPriceFixed(firstNonNull(sku.getMemberPriceFixed(), spu.getMemberPriceFixed()));
		snapshot.setMemberPriceFloor(firstNonNull(sku.getMemberPriceFloor(), spu.getMemberPriceFloor()));
		snapshot.setClearanceItem(resolveTag(sku.getIsClearanceItem(), spu.getIsClearanceItem()));
		snapshot.setClearancePrice(firstNonNull(sku.getClearancePrice(), spu.getClearancePrice()));
		snapshot.setClearanceDiscountRate(
				firstNonNull(sku.getClearanceDiscountRate(), spu.getClearanceDiscountRate()));
		snapshot.setClearanceExpireTime(
				firstNonNull(sku.getClearanceExpireTime(), spu.getClearanceExpireTime()));
		return snapshot;
	}

	private boolean resolveTag(Integer skuTag, Integer spuTag) {
		if (skuTag != null) {
			return skuTag == 1;
		}
		return Objects.equals(spuTag, 1);
	}

	private <T> T firstNonNull(T overrideValue, T fallback) {
		return overrideValue != null ? overrideValue : fallback;
	}

	private GoodsZoneTagVO.SkuTagVO toSkuTagVO(GoodsSku sku) {
		GoodsZoneTagVO.SkuTagVO vo = new GoodsZoneTagVO.SkuTagVO();
		vo.setSkuId(sku.getId());
		vo.setSpecsName(formatSpecs(sku.getSpecsArr()));
		vo.setSalesPrice(sku.getSalesPrice());
		vo.setOriginalPrice(sku.getOriginalPrice());
		vo.setIsMemberItem(sku.getIsMemberItem());
		vo.setMemberDiscountRate(sku.getMemberDiscountRate());
		vo.setMemberPriceFixed(sku.getMemberPriceFixed());
		vo.setMemberPriceFloor(sku.getMemberPriceFloor());
		vo.setIsClearanceItem(sku.getIsClearanceItem());
		vo.setClearancePrice(sku.getClearancePrice());
		vo.setClearanceDiscountRate(sku.getClearanceDiscountRate());
		vo.setClearanceExpireTime(sku.getClearanceExpireTime());
		vo.setHasOverride(stateResolver.hasZoneOverride(sku));
		return vo;
	}

	private String formatSpecs(List<GoodsSku.Specs> specs) {
		if (CollectionUtils.isEmpty(specs)) {
			return "";
		}
		return specs.stream()
			.filter(Objects::nonNull)
			.map(spec -> StringUtils.hasText(spec.getSpecsValueName()) ? spec.getSpecsValueName()
					: spec.getSpecsValueId())
			.filter(StringUtils::hasText)
			.collect(Collectors.joining(" / "));
	}

	private GoodsSpu requireSpu(String spuId) {
		GoodsSpu spu = StringUtils.hasText(spuId) ? goodsSpuMapper.selectById(spuId) : null;
		if (spu == null) {
			throw new ArynBusinessException("商品不存在或无权操作");
		}
		return spu;
	}

	private void applySpuTags(GoodsSpu spu, Boolean memberTag, BigDecimal memberRate, BigDecimal memberFixed,
			BigDecimal memberFloor, Boolean clearanceTag, BigDecimal clearancePrice, BigDecimal clearanceRate,
			LocalDateTime clearanceExpireTime, boolean clearMemberRule, boolean clearClearanceRule) {
		boolean memberRuleProvided = memberRate != null || memberFixed != null || memberFloor != null;
		boolean clearanceRuleProvided = clearancePrice != null || clearanceRate != null
				|| clearanceExpireTime != null;
		if (Boolean.TRUE.equals(clearanceTag) || clearanceRuleProvided) {
			GoodsZoneTagRuleValidator.validateClearanceAvailability(Boolean.TRUE.equals(clearanceTag),
					clearancePrice, clearanceRate, spu.getClearancePrice(), spu.getClearanceDiscountRate(),
					loadDefaultRate(GoodsZoneTagStateResolver.ZONE_CLEARANCE), spu.getOriginalPrice());
		}
		boolean memberTouched = memberTag != null || memberRuleProvided || clearMemberRule;
		boolean clearanceTouched = clearanceTag != null || clearanceRuleProvided || clearClearanceRule;
		if (!memberTouched && !clearanceTouched) {
			return;
		}
		var update = Wrappers.<GoodsSpu>lambdaUpdate().eq(GoodsSpu::getId, spu.getId());
		if (Boolean.FALSE.equals(memberTag)) {
			update.set(GoodsSpu::getIsMemberItem, 0)
				.set(GoodsSpu::getMemberDiscountRate, null)
				.set(GoodsSpu::getMemberPriceFixed, null)
				.set(GoodsSpu::getMemberPriceFloor, null);
		}
		else {
			if (Boolean.TRUE.equals(memberTag)) {
				update.set(GoodsSpu::getIsMemberItem, 1);
			}
			if (clearMemberRule) {
				update.set(GoodsSpu::getMemberDiscountRate, null)
					.set(GoodsSpu::getMemberPriceFixed, null)
					.set(GoodsSpu::getMemberPriceFloor, null);
			}
			else {
				update.set(memberRate != null, GoodsSpu::getMemberDiscountRate, memberRate)
					.set(memberFixed != null, GoodsSpu::getMemberPriceFixed, memberFixed)
					.set(memberFloor != null, GoodsSpu::getMemberPriceFloor, memberFloor);
			}
		}
		if (Boolean.FALSE.equals(clearanceTag)) {
			update.set(GoodsSpu::getIsClearanceItem, 0)
				.set(GoodsSpu::getClearancePrice, null)
				.set(GoodsSpu::getClearanceDiscountRate, null)
				.set(GoodsSpu::getClearanceExpireTime, null);
		}
		else {
			if (Boolean.TRUE.equals(clearanceTag)) {
				update.set(GoodsSpu::getIsClearanceItem, 1);
			}
			if (clearClearanceRule) {
				update.set(GoodsSpu::getClearancePrice, null)
					.set(GoodsSpu::getClearanceDiscountRate, null)
					.set(GoodsSpu::getClearanceExpireTime, null);
			}
			else {
				update.set(clearancePrice != null, GoodsSpu::getClearancePrice, clearancePrice)
					.set(clearanceRate != null, GoodsSpu::getClearanceDiscountRate, clearanceRate)
					.set(clearanceExpireTime != null, GoodsSpu::getClearanceExpireTime, clearanceExpireTime);
			}
		}
		goodsSpuMapper.update(null, update);
	}

	/**
	 * SPU 级专区规则变更落价格历史。
	 *
	 * <p>只记录本次真正触碰且发生变化的字段，避免对未触碰专区写「旧值 → null」的幽灵历史。
	 */
	private void recordSpuTagChanges(GoodsSpu stored, boolean memberTouched, boolean clearMemberRule,
			Boolean memberTag, BigDecimal memberRate, BigDecimal memberFixed, BigDecimal memberFloor,
			boolean clearanceTouched, boolean clearClearanceRule, Boolean clearanceTag,
			BigDecimal clearancePrice, BigDecimal clearanceRate) {
		String spuId = stored.getId();
		if (memberTouched) {
			boolean clear = Boolean.FALSE.equals(memberTag) || clearMemberRule;
			if (clear) {
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_rate",
					stored.getMemberDiscountRate(), null, "tag_save",
					Boolean.FALSE.equals(memberTag) ? "取消会员标签" : "清空会员规则");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_fixed",
					stored.getMemberPriceFixed(), null, "tag_save",
					Boolean.FALSE.equals(memberTag) ? "取消会员标签" : "清空会员规则");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_floor",
					stored.getMemberPriceFloor(), null, "tag_save",
					Boolean.FALSE.equals(memberTag) ? "取消会员标签" : "清空会员规则");
			}
			else {
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_rate",
					stored.getMemberDiscountRate(), memberRate, "tag_save", "修改会员折扣率");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_fixed",
					stored.getMemberPriceFixed(), memberFixed, "tag_save", "修改会员固定价");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "member_floor",
					stored.getMemberPriceFloor(), memberFloor, "tag_save", "修改会员价下限");
			}
		}
		if (clearanceTouched) {
			boolean clear = Boolean.FALSE.equals(clearanceTag) || clearClearanceRule;
			if (clear) {
				goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance", stored.getClearancePrice(),
					null, "tag_save",
					Boolean.FALSE.equals(clearanceTag) ? "取消出清标签" : "清空出清规则");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance_rate",
					stored.getClearanceDiscountRate(), null, "tag_save",
					Boolean.FALSE.equals(clearanceTag) ? "取消出清标签" : "清空出清规则");
			}
			else {
				goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance", stored.getClearancePrice(),
					clearancePrice, "tag_save", "修改出清价");
				goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance_rate",
					stored.getClearanceDiscountRate(), clearanceRate, "tag_save", "修改出清折扣率");
			}
		}
	}

	/** SKU 级专区规则精调落价格历史。 */
	private void recordSkuTagChanges(String spuId, GoodsSku stored, GoodsZoneTagSaveDTO.SkuTag item,
			Integer memberTag, Integer clearanceTag) {
		if (stored == null || item == null) {
			return;
		}
		if (!Objects.equals(memberTag, 0)) {
			goodsPriceHistoryService.recordIfChanged(spuId, stored.getId(), "member_rate",
				stored.getMemberDiscountRate(), item.getMemberDiscountRate(), "tag_save", "SKU会员折扣率精调");
			goodsPriceHistoryService.recordIfChanged(spuId, stored.getId(), "member_fixed",
				stored.getMemberPriceFixed(), item.getMemberPriceFixed(), "tag_save", "SKU会员固定价精调");
		}
		if (!Objects.equals(clearanceTag, 0)) {
			goodsPriceHistoryService.recordIfChanged(spuId, stored.getId(), "clearance",
				stored.getClearancePrice(), item.getClearancePrice(), "tag_save", "SKU出清价精调");
		}
	}

	/** 重新打标：SKU 标签回归继承（NULL），保留价格精调字段；只有存在显式 0 时才写库。 */
	private void resetSkuMemberTagIfNeeded(String spuId) {
		long explicitUntag = goodsSkuMapper.selectCount(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getSpuId, spuId)
			.eq(GoodsSku::getIsMemberItem, 0));
		if (explicitUntag > 0) {
			goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
				.eq(GoodsSku::getSpuId, spuId)
				.set(GoodsSku::getIsMemberItem, null));
		}
	}

	private void resetSkuClearanceTagIfNeeded(String spuId) {
		long explicitUntag = goodsSkuMapper.selectCount(Wrappers.<GoodsSku>lambdaQuery()
			.eq(GoodsSku::getSpuId, spuId)
			.eq(GoodsSku::getIsClearanceItem, 0));
		if (explicitUntag > 0) {
			goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
				.eq(GoodsSku::getSpuId, spuId)
				.set(GoodsSku::getIsClearanceItem, null));
		}
	}

	private void clearSkuMember(String spuId, boolean untag) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsMemberItem, untag ? Integer.valueOf(0) : null)
			.set(GoodsSku::getMemberDiscountRate, null)
			.set(GoodsSku::getMemberPriceFixed, null)
			.set(GoodsSku::getMemberPriceFloor, null));
	}

	private void clearSkuClearance(String spuId, boolean untag) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsClearanceItem, untag ? Integer.valueOf(0) : null)
			.set(GoodsSku::getClearancePrice, null)
			.set(GoodsSku::getClearanceDiscountRate, null)
			.set(GoodsSku::getClearanceExpireTime, null));
	}

	private void validateProvidedRules(GoodsZoneTagBatchDTO dto) {
		GoodsZoneTagRuleValidator.validateBatchPayload(dto);
	}

	private void validateProvidedRules(GoodsZoneTagSaveDTO dto) {
		GoodsZoneTagRuleValidator.validateSavePayload(dto);
	}

	private BigDecimal loadDefaultRate(String zone) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId) || remoteMallZoneSettingService == null) {
			return null;
		}
		try {
			MallZoneSettingSnapshotVO setting = remoteMallZoneSettingService.getSetting(tenantId, zone);
			return setting == null ? null : setting.getDefaultDiscountRate();
		}
		catch (Exception ex) {
			log.warn("读取专区默认折扣失败 tenant={} zone={}，按未配置处理", tenantId, zone, ex);
			return null;
		}
	}

	private void validateRate(BigDecimal rate, String label) {
		if (rate != null && (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0)) {
			throw new ArynBusinessException(label + "必须在 (0, 1) 区间内，如 0.9 表示 9 折");
		}
	}

	private void validatePositive(BigDecimal value, String label) {
		if (value != null && value.signum() <= 0) {
			throw new ArynBusinessException(label + "必须大于 0");
		}
	}

}
