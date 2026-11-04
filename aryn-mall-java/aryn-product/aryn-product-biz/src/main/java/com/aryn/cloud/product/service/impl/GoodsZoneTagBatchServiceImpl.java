package com.aryn.cloud.product.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagQueryDTO;
import com.aryn.cloud.product.api.dto.ZoneTagPersistSnapshot;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatch;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatchItem;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneSkuStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchItemVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchResultVO;
import com.aryn.cloud.product.api.vo.ZoneTagChangeVO;
import com.aryn.cloud.product.api.vo.ZoneTagItemStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagPreviewVO;
import com.aryn.cloud.product.api.vo.ZoneTagWarningVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IGoodsZoneTagBatchService;
import com.aryn.cloud.product.service.IZoneTagBatchRecordService;
import com.aryn.cloud.product.service.support.GoodsZoneTagRuleValidator;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.aryn.cloud.product.service.support.GoodsZoneTagWriter;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 专区标签批量操作实现：预检、执行、状态查询。
 *
 * <p>批量按一次 SPU IN 查询 + 一次 SKU IN 查询加载，避免逐条 N+1；执行只对
 * 「判定有变化」的商品落库，并写批次记录（含变更前后快照，供撤销）。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsZoneTagBatchServiceImpl implements IGoodsZoneTagBatchService {

	/** 单次批量上限：超过由前端分片。 */
	private static final int MAX_BATCH_SIZE = 200;

	/** 「先涨后降」提示窗口。 */
	private static final int RECENT_SALES_DAYS = 30;

	private static final DateTimeFormatter BATCH_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsZoneTagStateResolver stateResolver;

	private final GoodsZoneTagWriter tagWriter;

	private final GoodsPriceHistoryService goodsPriceHistoryService;

	private final IZoneTagBatchRecordService zoneTagBatchRecordService;

	/**
	 * 专区默认折扣：批量预检/执行的兜底口径。
	 *
	 * <p>非 final + @DubboReference（构造器注入远程接口曾在 boot 启动失败）。
	 */
	@DubboReference
	private RemoteMallZoneSettingService remoteMallZoneSettingService;

	@Override
	public ZoneTagPreviewVO preview(GoodsZoneTagBatchDTO dto) {
		normalizeActions(dto);
		// 预检允许尚未选择动作（全部 NONE），否则弹窗刚打开就会因「请选择操作」报错
		GoodsZoneTagRuleValidator.validatePreviewPayload(dto);
		List<String> spuIds = normalizeSpuIds(dto);
		PreparedContext context = loadContext(spuIds);
		ZoneTagPreviewVO vo = new ZoneTagPreviewVO();
		ZoneTagPreviewVO.Summary summary = new ZoneTagPreviewVO.Summary();
		summary.setTotal(0);
		summary.setAdd(0);
		summary.setUpdate(0);
		summary.setNoChange(0);
		summary.setRemove(0);
		summary.setInvalid(0);
		summary.setWarningCount(0);
		summary.setSkuOverridesWillClear(0);
		Map<String, ZoneTagPreviewVO.Zone> zones = initZoneSummaries();
		List<ZoneTagItemStateVO> items = new ArrayList<>();
		for (String spuId : spuIds) {
			GoodsSpu spu = context.spuMap().get(spuId);
			ZoneTagItemStateVO item;
			if (spu == null) {
				item = missingItem(spuId, dto);
			}
			else {
				item = stateResolver.previewItem(spu, context.skuMap().getOrDefault(spuId, List.of()), dto,
						context.memberDefaultRate(), context.clearanceDefaultRate(),
						context.recentSalesIncrease().contains(spuId));
			}
			items.add(item);
			summarize(summary, zones, item, dto);
		}
		vo.setSummary(summary);
		vo.setZones(new ArrayList<>(zones.values()));
		vo.setItems(items);
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ZoneTagBatchResultVO execute(GoodsZoneTagBatchDTO dto) {
		normalizeActions(dto);
		GoodsZoneTagRuleValidator.validateBatchPayload(dto);
		List<String> spuIds = normalizeSpuIds(dto);
		PreparedContext context = loadContext(spuIds);
		String tenantId = ArynTenantContextHolder.getTenantId();
		ArynUser user = currentUser();
		String reason = reasonOf(dto);
		ZoneTagBatchResultVO.Summary summary = emptyBatchSummary(spuIds.size());
		Map<String, ZoneTagBatchResultVO.Zone> zoneSummaries = initBatchZoneSummaries();
		List<ZoneTagBatchItemVO> resultItems = new ArrayList<>();
		List<GoodsZoneTagBatchItem> batchItems = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();
		for (String spuId : spuIds) {
			GoodsSpu spu = context.spuMap().get(spuId);
			if (spu == null) {
				ZoneTagBatchItemVO failed = failedItem(spuId, null, "SPU_NOT_FOUND", "商品不存在或无权操作",
						List.of());
				resultItems.add(failed);
				batchItems.add(batchItem(tenantId, spuId, null, "FAILED", false, "SPU_NOT_FOUND",
						"商品不存在或无权操作", null, null, now));
				summary.setFailed(summary.getFailed() + 1);
				incrementZoneFailed(zoneSummaries, memberRequested(dto), clearanceRequested(dto));
				continue;
			}
			List<GoodsSku> skus = context.skuMap().getOrDefault(spuId, List.of());
			List<ZoneTagChangeVO> changes = new ArrayList<>();
			changes.add(stateResolver.evaluateMember(spu, skus, dto, context.memberDefaultRate()));
			changes.add(stateResolver.evaluateClearance(spu, skus, dto, context.clearanceDefaultRate()));
			List<ZoneTagWarningVO> itemWarnings = stateResolver.itemWarnings(spu,
					context.recentSalesIncrease().contains(spuId));
			ZoneTagChangeVO invalid = changes.stream().filter(c -> "INVALID".equals(c.getType())).findFirst()
				.orElse(null);
			if (invalid != null) {
				String message = firstWarning(invalid);
				ZoneTagBatchItemVO failed = failedItem(spuId, spu.getName(), "INVALID_RULE", message, changes);
				failed.setWarnings(itemWarnings);
				resultItems.add(failed);
				ZoneRuleSnapshotVO before = stateResolver.snapshot(spu, skus);
				batchItems.add(batchItem(tenantId, spuId, spu.getName(), "INVALID", false, "INVALID_RULE",
						message, JSONUtil.toJsonStr(persistSnapshot(before, skus)), null, now));
				summary.setFailed(summary.getFailed() + 1);
				incrementZoneInvalid(zoneSummaries, changes);
				continue;
			}
			boolean memberTouched = touched(dto.getMemberTag(), changes, GoodsZoneTagStateResolver.ZONE_MEMBER);
			boolean clearanceTouched = touched(dto.getClearanceTag(), changes,
					GoodsZoneTagStateResolver.ZONE_CLEARANCE);
			ZoneRuleSnapshotVO beforeRule = stateResolver.snapshot(spu, skus);
			String primaryType = primaryChangeType(changes);
			if (!memberTouched && !clearanceTouched) {
				ZoneTagBatchItemVO noChange = successItem(spu, "NO_CHANGE", changes, itemWarnings);
				resultItems.add(noChange);
				batchItems.add(batchItem(tenantId, spuId, spu.getName(), "NO_CHANGE", true, null, "OK",
						JSONUtil.toJsonStr(persistSnapshot(beforeRule, skus)),
						JSONUtil.toJsonStr(persistSnapshot(beforeRule, skus)), now));
				summary.setNoChange(summary.getNoChange() + 1);
				summary.setSuccess(summary.getSuccess() + 1);
				incrementZoneNoChange(zoneSummaries, changes);
				continue;
			}
			ZoneRuleSnapshotVO afterRule = tagWriter.applyBatch(spu, skus, dto, memberTouched, clearanceTouched,
					reason);
			List<ZoneSkuStateVO> afterSkus = skuStatesAfter(skus, dto, memberTouched, clearanceTouched);
			ZoneTagBatchItemVO item = successItem(spu, primaryType, fillChangeSnapshots(changes, beforeRule, afterRule),
					itemWarnings);
			resultItems.add(item);
			batchItems.add(batchItem(tenantId, spuId, spu.getName(), primaryType, true, null, "OK",
					JSONUtil.toJsonStr(persistSnapshot(beforeRule, skus)),
					JSONUtil.toJsonStr(afterSnapshot(afterRule, afterSkus)), now));
			applySummary(summary, primaryType);
			applyZoneSummary(zoneSummaries, changes);
		}
		String batchId = IdUtil.getSnowflakeNextIdStr();
		GoodsZoneTagBatch batch = buildBatch(tenantId, batchId, user, dto, summary);
		zoneTagBatchRecordService.saveBatch(batch, batchItems);
		ZoneTagBatchResultVO vo = new ZoneTagBatchResultVO();
		vo.setBatchId(batch.getId());
		vo.setSummary(summary);
		vo.setZones(new ArrayList<>(zoneSummaries.values()));
		vo.setSync(skippedSync());
		vo.setItems(resultItems);
		return vo;
	}

	@Override
	public List<ZoneTagItemStateVO> query(GoodsZoneTagQueryDTO dto) {
		if (dto == null || CollectionUtils.isEmpty(dto.getSpuIds())) {
			throw new ArynBusinessException("请选择商品");
		}
		List<String> spuIds = normalizeIds(dto.getSpuIds());
		PreparedContext context = loadContext(spuIds);
		boolean withSkus = Boolean.TRUE.equals(dto.getWithSkus());
		List<ZoneTagItemStateVO> items = new ArrayList<>();
		for (String spuId : spuIds) {
			GoodsSpu spu = context.spuMap().get(spuId);
			if (spu == null) {
				ZoneTagItemStateVO item = new ZoneTagItemStateVO();
				item.setSpuId(spuId);
				item.setWarnings(List.of(GoodsZoneTagStateResolver.warning("SPU_NOT_FOUND", "商品不存在或无权操作")));
				items.add(item);
				continue;
			}
			List<GoodsSku> skus = context.skuMap().getOrDefault(spuId, List.of());
			ZoneTagItemStateVO item = new ZoneTagItemStateVO();
			item.setSpuId(spuId);
			item.setSpuName(spu.getName());
			item.setSpuStatus(spu.getStatus());
			item.setBefore(stateResolver.snapshot(spu, skus));
			item.setWarnings(stateResolver.itemWarnings(spu, false));
			if (withSkus) {
				item.setSkus(stateResolver.skuStates(skus));
			}
			items.add(item);
		}
		return items;
	}

	// ------------------------------------------------------------------ 汇总

	private void summarize(ZoneTagPreviewVO.Summary summary, Map<String, ZoneTagPreviewVO.Zone> zones,
			ZoneTagItemStateVO item, GoodsZoneTagBatchDTO dto) {
		summary.setTotal(summary.getTotal() + 1);
		boolean anyAdd = false;
		boolean anyUpdate = false;
		boolean anyRemove = false;
		boolean anyInvalid = false;
		int warnings = item.getWarnings() == null ? 0 : item.getWarnings().size();
		for (ZoneTagChangeVO change : safeChanges(item)) {
			warnings += change.getWarnings() == null ? 0 : change.getWarnings().size();
			ZoneTagPreviewVO.Zone zone = zones.get(change.getZone());
			if (zone == null) {
				continue;
			}
			if (item.getBefore() != null && "1".equals(String.valueOf(tagValue(item, change.getZone())))) {
				zone.setExisting(zone.getExisting() + 1);
			}
			switch (change.getType()) {
				case "ADD" -> {
					zone.setWillAdd(zone.getWillAdd() + 1);
					anyAdd = true;
				}
				case "UPDATE" -> {
					zone.setWillUpdate(zone.getWillUpdate() + 1);
					anyUpdate = true;
				}
				case "REMOVE" -> {
					zone.setWillRemove(zone.getWillRemove() + 1);
					anyRemove = true;
				}
				case "INVALID" -> {
					zone.setInvalid(zone.getInvalid() + 1);
					anyInvalid = true;
				}
				default -> zone.setNoChange(zone.getNoChange() + 1);
			}
			int overrides = item.getBefore() == null || item.getBefore().getSkuOverrideCount() == null ? 0
					: item.getBefore().getSkuOverrideCount();
			if (overrides > 0 && !"NO_CHANGE".equals(change.getType())) {
				zone.setWithSkuOverride(zone.getWithSkuOverride() + 1);
			}
		}
		if (anyInvalid) {
			summary.setInvalid(summary.getInvalid() + 1);
		}
		else if (anyAdd) {
			summary.setAdd(summary.getAdd() + 1);
		}
		else if (anyUpdate) {
			summary.setUpdate(summary.getUpdate() + 1);
		}
		else if (anyRemove) {
			summary.setRemove(summary.getRemove() + 1);
		}
		else {
			summary.setNoChange(summary.getNoChange() + 1);
		}
		summary.setWarningCount(summary.getWarningCount() + warnings);
		if (Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && item.getBefore() != null
				&& item.getBefore().getSkuOverrideCount() != null) {
			summary.setSkuOverridesWillClear(
					summary.getSkuOverridesWillClear() + item.getBefore().getSkuOverrideCount());
		}
	}

	private Object tagValue(ZoneTagItemStateVO item, String zone) {
		if (item.getBefore() == null) {
			return 0;
		}
		if (GoodsZoneTagStateResolver.ZONE_MEMBER.equals(zone)) {
			return Objects.requireNonNullElse(item.getBefore().getMemberTag(), 0);
		}
		return Objects.requireNonNullElse(item.getBefore().getClearanceTag(), 0);
	}

	private Map<String, ZoneTagPreviewVO.Zone> initZoneSummaries() {
		Map<String, ZoneTagPreviewVO.Zone> zones = new LinkedHashMap<>();
		zones.put(GoodsZoneTagStateResolver.ZONE_MEMBER, emptyPreviewZone(GoodsZoneTagStateResolver.ZONE_MEMBER));
		zones.put(GoodsZoneTagStateResolver.ZONE_CLEARANCE,
				emptyPreviewZone(GoodsZoneTagStateResolver.ZONE_CLEARANCE));
		return zones;
	}

	private ZoneTagPreviewVO.Zone emptyPreviewZone(String zone) {
		ZoneTagPreviewVO.Zone summary = new ZoneTagPreviewVO.Zone();
		summary.setZone(zone);
		summary.setExisting(0);
		summary.setWillAdd(0);
		summary.setWillUpdate(0);
		summary.setWillRemove(0);
		summary.setNoChange(0);
		summary.setWithSkuOverride(0);
		summary.setInvalid(0);
		return summary;
	}

	private ZoneTagBatchResultVO.Summary emptyBatchSummary(int requested) {
		ZoneTagBatchResultVO.Summary summary = new ZoneTagBatchResultVO.Summary();
		summary.setRequested(requested);
		summary.setSuccess(0);
		summary.setChanged(0);
		summary.setAdded(0);
		summary.setUpdated(0);
		summary.setRemoved(0);
		summary.setNoChange(0);
		summary.setFailed(0);
		return summary;
	}

	private Map<String, ZoneTagBatchResultVO.Zone> initBatchZoneSummaries() {
		Map<String, ZoneTagBatchResultVO.Zone> zones = new LinkedHashMap<>();
		zones.put(GoodsZoneTagStateResolver.ZONE_MEMBER, emptyBatchZone(GoodsZoneTagStateResolver.ZONE_MEMBER));
		zones.put(GoodsZoneTagStateResolver.ZONE_CLEARANCE,
				emptyBatchZone(GoodsZoneTagStateResolver.ZONE_CLEARANCE));
		return zones;
	}

	private ZoneTagBatchResultVO.Zone emptyBatchZone(String zone) {
		ZoneTagBatchResultVO.Zone summary = new ZoneTagBatchResultVO.Zone();
		summary.setZone(zone);
		summary.setAdded(0);
		summary.setUpdated(0);
		summary.setRemoved(0);
		summary.setNoChange(0);
		summary.setFailed(0);
		return summary;
	}

	private void applySummary(ZoneTagBatchResultVO.Summary summary, String primaryType) {
		summary.setSuccess(summary.getSuccess() + 1);
		switch (primaryType) {
			case "ADD" -> {
				summary.setAdded(summary.getAdded() + 1);
				summary.setChanged(summary.getChanged() + 1);
			}
			case "UPDATE" -> {
				summary.setUpdated(summary.getUpdated() + 1);
				summary.setChanged(summary.getChanged() + 1);
			}
			case "REMOVE" -> {
				summary.setRemoved(summary.getRemoved() + 1);
				summary.setChanged(summary.getChanged() + 1);
			}
			default -> summary.setNoChange(summary.getNoChange() + 1);
		}
	}

	private void applyZoneSummary(Map<String, ZoneTagBatchResultVO.Zone> zones, List<ZoneTagChangeVO> changes) {
		for (ZoneTagChangeVO change : changes) {
			ZoneTagBatchResultVO.Zone zone = zones.get(change.getZone());
			if (zone == null) {
				continue;
			}
			switch (change.getType()) {
				case "ADD" -> zone.setAdded(zone.getAdded() + 1);
				case "UPDATE" -> zone.setUpdated(zone.getUpdated() + 1);
				case "REMOVE" -> zone.setRemoved(zone.getRemoved() + 1);
				case "NO_CHANGE" -> zone.setNoChange(zone.getNoChange() + 1);
				default -> zone.setFailed(zone.getFailed() + 1);
			}
		}
	}

	private void incrementZoneFailed(Map<String, ZoneTagBatchResultVO.Zone> zones, boolean member,
			boolean clearance) {
		if (member) {
			zones.get(GoodsZoneTagStateResolver.ZONE_MEMBER).setFailed(
					zones.get(GoodsZoneTagStateResolver.ZONE_MEMBER).getFailed() + 1);
		}
		if (clearance) {
			zones.get(GoodsZoneTagStateResolver.ZONE_CLEARANCE).setFailed(
					zones.get(GoodsZoneTagStateResolver.ZONE_CLEARANCE).getFailed() + 1);
		}
	}

	private void incrementZoneInvalid(Map<String, ZoneTagBatchResultVO.Zone> zones,
			List<ZoneTagChangeVO> changes) {
		for (ZoneTagChangeVO change : changes) {
			if ("INVALID".equals(change.getType())) {
				ZoneTagBatchResultVO.Zone zone = zones.get(change.getZone());
				if (zone != null) {
					zone.setFailed(zone.getFailed() + 1);
				}
			}
		}
	}

	private void incrementZoneNoChange(Map<String, ZoneTagBatchResultVO.Zone> zones,
			List<ZoneTagChangeVO> changes) {
		for (ZoneTagChangeVO change : changes) {
			if ("NO_CHANGE".equals(change.getType())) {
				ZoneTagBatchResultVO.Zone zone = zones.get(change.getZone());
				if (zone != null) {
					zone.setNoChange(zone.getNoChange() + 1);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 构造结果

	private ZoneTagItemStateVO missingItem(String spuId, GoodsZoneTagBatchDTO dto) {
		ZoneTagItemStateVO item = new ZoneTagItemStateVO();
		item.setSpuId(spuId);
		List<ZoneTagChangeVO> changes = new ArrayList<>();
		if (memberRequested(dto)) {
			changes.add(invalidChange(GoodsZoneTagStateResolver.ZONE_MEMBER));
		}
		if (clearanceRequested(dto)) {
			changes.add(invalidChange(GoodsZoneTagStateResolver.ZONE_CLEARANCE));
		}
		item.setChanges(changes);
		item.setWarnings(List.of(GoodsZoneTagStateResolver.warning("SPU_NOT_FOUND", "商品不存在或无权操作")));
		return item;
	}

	private ZoneTagChangeVO invalidChange(String zone) {
		ZoneTagChangeVO change = new ZoneTagChangeVO();
		change.setZone(zone);
		change.setType("INVALID");
		change.setWarnings(List.of(GoodsZoneTagStateResolver.warning("SPU_NOT_FOUND", "商品不存在或无权操作")));
		return change;
	}

	private ZoneTagBatchItemVO successItem(GoodsSpu spu, String changeType, List<ZoneTagChangeVO> changes,
			List<ZoneTagWarningVO> warnings) {
		ZoneTagBatchItemVO item = new ZoneTagBatchItemVO();
		item.setSpuId(spu.getId());
		item.setSpuName(spu.getName());
		item.setSuccess(true);
		item.setChangeType(changeType);
		item.setChanges(changes);
		item.setMessage("OK");
		item.setWarnings(warnings);
		return item;
	}

	private ZoneTagBatchItemVO failedItem(String spuId, String spuName, String errorCode, String message,
			List<ZoneTagChangeVO> changes) {
		ZoneTagBatchItemVO item = new ZoneTagBatchItemVO();
		item.setSpuId(spuId);
		item.setSpuName(spuName);
		item.setSuccess(false);
		item.setChangeType("INVALID".equals(errorCode) || "INVALID_RULE".equals(errorCode) ? "INVALID" : "FAILED");
		item.setChanges(changes);
		item.setMessage(message);
		item.setErrorCode(errorCode);
		return item;
	}

	private List<ZoneTagChangeVO> fillChangeSnapshots(List<ZoneTagChangeVO> changes, ZoneRuleSnapshotVO before,
			ZoneRuleSnapshotVO after) {
		for (ZoneTagChangeVO change : changes) {
			if ("NO_CHANGE".equals(change.getType())) {
				continue;
			}
			change.setBefore(zoneSnapshotMap(before, change.getZone()));
			change.setAfter(zoneSnapshotMap(after, change.getZone()));
		}
		return changes;
	}

	private Map<String, Object> zoneSnapshotMap(ZoneRuleSnapshotVO rule, String zone) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (GoodsZoneTagStateResolver.ZONE_MEMBER.equals(zone)) {
			map.put("memberTag", rule.getMemberTag());
			map.put("memberDiscountRate", rule.getMemberDiscountRate());
			map.put("memberPriceFixed", rule.getMemberPriceFixed());
			map.put("memberPriceFloor", rule.getMemberPriceFloor());
		}
		else {
			map.put("clearanceTag", rule.getClearanceTag());
			map.put("clearancePrice", rule.getClearancePrice());
			map.put("clearanceDiscountRate", rule.getClearanceDiscountRate());
			map.put("clearanceExpireTime", rule.getClearanceExpireTime());
		}
		return map;
	}

	private ZoneTagPersistSnapshot persistSnapshot(ZoneRuleSnapshotVO rule, List<GoodsSku> skus) {
		ZoneTagPersistSnapshot snapshot = new ZoneTagPersistSnapshot();
		snapshot.setRule(rule);
		snapshot.setSkus(stateResolver.skuStates(skus));
		return snapshot;
	}

	private ZoneTagPersistSnapshot afterSnapshot(ZoneRuleSnapshotVO rule, List<ZoneSkuStateVO> skus) {
		ZoneTagPersistSnapshot snapshot = new ZoneTagPersistSnapshot();
		snapshot.setRule(rule);
		snapshot.setSkus(skus);
		return snapshot;
	}

	private List<ZoneSkuStateVO> skuStatesAfter(List<GoodsSku> skus, GoodsZoneTagBatchDTO dto,
			boolean memberTouched, boolean clearanceTouched) {
		if (CollectionUtils.isEmpty(skus)) {
			return List.of();
		}
		boolean overwrite = Boolean.TRUE.equals(dto.getOverwriteSkuRules());
		List<ZoneSkuStateVO> states = new ArrayList<>();
		for (GoodsSku sku : skus) {
			ZoneSkuStateVO vo = stateResolver.toSkuState(sku);
			if (memberTouched) {
				if (Boolean.FALSE.equals(dto.getMemberTag())) {
					vo.setIsMemberItem(0);
					vo.setMemberDiscountRate(null);
					vo.setMemberPriceFixed(null);
					vo.setMemberPriceFloor(null);
				}
				else if (overwrite) {
					vo.setIsMemberItem(null);
					vo.setMemberDiscountRate(null);
					vo.setMemberPriceFixed(null);
					vo.setMemberPriceFloor(null);
				}
			}
			if (clearanceTouched) {
				if (Boolean.FALSE.equals(dto.getClearanceTag())) {
					vo.setIsClearanceItem(0);
					vo.setClearancePrice(null);
					vo.setClearanceDiscountRate(null);
					vo.setClearanceExpireTime(null);
				}
				else if (overwrite) {
					vo.setIsClearanceItem(null);
					vo.setClearancePrice(null);
					vo.setClearanceDiscountRate(null);
					vo.setClearanceExpireTime(null);
				}
			}
			vo.setHasOverride(stateResolver.hasZoneOverrideFromState(vo));
			states.add(vo);
		}
		return states;
	}

	private GoodsZoneTagBatch buildBatch(String tenantId, String batchId, ArynUser user,
			GoodsZoneTagBatchDTO dto, ZoneTagBatchResultVO.Summary summary) {
		GoodsZoneTagBatch batch = new GoodsZoneTagBatch();
		batch.setId(batchId);
		batch.setTenantId(tenantId);
		batch.setBatchNo("Z" + LocalDateTime.now().format(BATCH_NO_FORMAT) + batchId.substring(batchId.length() - 4));
		if (user != null) {
			batch.setOperatorId(user.getUserId());
			batch.setOperatorName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
		}
		batch.setMemberAction(actionOf(dto.getMemberTag()));
		batch.setClearanceAction(actionOf(dto.getClearanceTag()));
		Map<String, Object> memberRule = new LinkedHashMap<>();
		memberRule.put("discountRate", dto.getMemberDiscountRate());
		memberRule.put("fixedPrice", dto.getMemberPriceFixed());
		Map<String, Object> clearanceRule = new LinkedHashMap<>();
		clearanceRule.put("price", dto.getClearancePrice());
		clearanceRule.put("discountRate", dto.getClearanceDiscountRate());
		clearanceRule.put("expireTime", dto.getClearanceExpireTime());
		batch.setMemberRule(JSONUtil.toJsonStr(memberRule));
		batch.setClearanceRule(JSONUtil.toJsonStr(clearanceRule));
		batch.setOverwriteSkuRules(dto.getOverwriteSkuRules());
		batch.setTotal(summary.getRequested());
		batch.setChanged(summary.getChanged());
		batch.setAdded(summary.getAdded());
		batch.setUpdated(summary.getUpdated());
		batch.setRemoved(summary.getRemoved());
		batch.setNoChange(summary.getNoChange());
		batch.setFailed(summary.getFailed());
		batch.setSyncStatus("SKIPPED");
		batch.setUndoStatus("NONE");
		batch.setCreateTime(LocalDateTime.now());
		batch.setDelFlag("0");
		return batch;
	}

	private GoodsZoneTagBatchItem batchItem(String tenantId, String spuId, String spuName, String changeType,
			boolean success, String errorCode, String message, String beforeSnapshot, String afterSnapshot,
			LocalDateTime now) {
		GoodsZoneTagBatchItem item = new GoodsZoneTagBatchItem();
		item.setTenantId(tenantId);
		item.setSpuId(spuId);
		item.setSpuName(spuName);
		item.setChangeType(changeType);
		item.setSuccess(success);
		item.setErrorCode(errorCode);
		item.setMessage(message);
		item.setBeforeSnapshot(beforeSnapshot);
		item.setAfterSnapshot(afterSnapshot);
		item.setCreateTime(now);
		item.setDelFlag("0");
		return item;
	}

	private ZoneTagBatchResultVO.Sync skippedSync() {
		ZoneTagBatchResultVO.Sync sync = new ZoneTagBatchResultVO.Sync();
		sync.setStatus("SKIPPED");
		return sync;
	}

	private String reasonOf(GoodsZoneTagBatchDTO dto) {
		boolean member = dto.getMemberTag() != null;
		boolean clearance = dto.getClearanceTag() != null;
		boolean removeOnly = member && !clearance && Boolean.FALSE.equals(dto.getMemberTag())
				|| clearance && !member && Boolean.FALSE.equals(dto.getClearanceTag());
		if (removeOnly) {
			return "批量移出专区";
		}
		return "批量专区操作";
	}

	private String actionOf(Boolean tag) {
		if (tag == null) {
			return "NONE";
		}
		return tag ? "ADD" : "REMOVE";
	}

	// ------------------------------------------------------------------ 工具

	/**
	 * 兼容显式动作（ADD/REMOVE/NONE）与旧布尔字段（memberTag/clearanceTag）两种入参：
	 * 前端预检按契约传 action，批量执行按契约 §2.2 传布尔；统一归一化为布尔后走同一套逻辑。
	 */
	private void normalizeActions(GoodsZoneTagBatchDTO dto) {
		if (dto == null) {
			return;
		}
		if (dto.getMemberAction() != null) {
			dto.setMemberTag(mapAction(dto.getMemberAction(), "memberAction"));
		}
		if (dto.getClearanceAction() != null) {
			dto.setClearanceTag(mapAction(dto.getClearanceAction(), "clearanceAction"));
		}
	}

	private Boolean mapAction(String action, String field) {
		String value = action.trim().toUpperCase(java.util.Locale.ROOT);
		return switch (value) {
			case "ADD" -> Boolean.TRUE;
			case "REMOVE" -> Boolean.FALSE;
			case "NONE", "" -> null;
			default -> throw new ArynBusinessException(field + " 仅支持 ADD / REMOVE / NONE");
		};
	}

	private List<String> normalizeSpuIds(GoodsZoneTagBatchDTO dto) {
		if (dto == null || CollectionUtils.isEmpty(dto.getSpuIds())) {
			throw new ArynBusinessException("请选择商品");
		}
		List<String> ids = normalizeIds(dto.getSpuIds());
		dto.setSpuIds(ids);
		return ids;
	}

	private List<String> normalizeIds(List<String> raw) {
		Set<String> ids = new LinkedHashSet<>();
		for (String id : raw) {
			if (StringUtils.hasText(id)) {
				ids.add(id.trim());
			}
		}
		if (ids.isEmpty()) {
			throw new ArynBusinessException("请选择商品");
		}
		if (ids.size() > MAX_BATCH_SIZE) {
			throw new ArynBusinessException("单次最多处理 " + MAX_BATCH_SIZE + " 个商品，请分批提交");
		}
		return new ArrayList<>(ids);
	}

	private PreparedContext loadContext(List<String> spuIds) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		List<GoodsSpu> spus = goodsSpuMapper.selectBatchIds(spuIds);
		Map<String, GoodsSpu> spuMap = spus.stream()
			.collect(Collectors.toMap(GoodsSpu::getId, spu -> spu, (a, b) -> a, LinkedHashMap::new));
		Map<String, List<GoodsSku>> skuMap = new HashMap<>();
		if (StringUtils.hasText(tenantId) && !spuMap.isEmpty()) {
			List<GoodsSku> skus = goodsSkuMapper.selectList(Wrappers.<GoodsSku>lambdaQuery()
				.in(GoodsSku::getSpuId, spuMap.keySet()));
			skuMap = skus.stream().collect(Collectors.groupingBy(GoodsSku::getSpuId));
		}
		Set<String> recent = goodsPriceHistoryService.recentSalesIncreaseSpuIds(spuIds, RECENT_SALES_DAYS);
		return new PreparedContext(spuMap, skuMap, loadDefaultRate(tenantId, GoodsZoneTagStateResolver.ZONE_MEMBER),
				loadDefaultRate(tenantId, GoodsZoneTagStateResolver.ZONE_CLEARANCE), recent);
	}

	private BigDecimal loadDefaultRate(String tenantId, String zone) {
		if (!StringUtils.hasText(tenantId)) {
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

	private ArynUser currentUser() {
		try {
			return SecurityUtils.getUser();
		}
		catch (Exception ex) {
			return null;
		}
	}

	private boolean touched(Boolean tag, List<ZoneTagChangeVO> changes, String zone) {
		if (tag == null) {
			return false;
		}
		return changes.stream()
			.filter(change -> zone.equals(change.getZone()))
			.anyMatch(change -> !"NO_CHANGE".equals(change.getType()));
	}

	private boolean memberRequested(GoodsZoneTagBatchDTO dto) {
		return dto.getMemberTag() != null;
	}

	private boolean clearanceRequested(GoodsZoneTagBatchDTO dto) {
		return dto.getClearanceTag() != null;
	}

	private String primaryChangeType(List<ZoneTagChangeVO> changes) {
		boolean add = changes.stream().anyMatch(change -> "ADD".equals(change.getType()));
		boolean update = changes.stream().anyMatch(change -> "UPDATE".equals(change.getType()));
		boolean remove = changes.stream().anyMatch(change -> "REMOVE".equals(change.getType()));
		if (add) {
			return "ADD";
		}
		if (update) {
			return "UPDATE";
		}
		if (remove) {
			return "REMOVE";
		}
		return "NO_CHANGE";
	}

	private String firstWarning(ZoneTagChangeVO change) {
		if (change.getWarnings() == null || change.getWarnings().isEmpty()) {
			return "专区规则校验未通过";
		}
		return change.getWarnings().get(0).getMessage();
	}

	private List<ZoneTagChangeVO> safeChanges(ZoneTagItemStateVO item) {
		return item.getChanges() == null ? List.of() : item.getChanges();
	}

	private record PreparedContext(Map<String, GoodsSpu> spuMap, Map<String, List<GoodsSku>> skuMap,
			BigDecimal memberDefaultRate, BigDecimal clearanceDefaultRate, Set<String> recentSalesIncrease) {
	}

}
