package com.aryn.cloud.product.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ZoneTagPersistSnapshot;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatch;
import com.aryn.cloud.product.api.entity.GoodsZoneTagBatchItem;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneSkuStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchDetailVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchItemVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchVO;
import com.aryn.cloud.product.api.vo.ZoneTagChangeVO;
import com.aryn.cloud.product.api.vo.ZoneTagUndoResultVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.GoodsZoneTagBatchItemMapper;
import com.aryn.cloud.product.mapper.GoodsZoneTagBatchMapper;
import com.aryn.cloud.product.mapper.ZoneGoodsQueryMapper;
import com.aryn.cloud.product.service.IZoneTagBatchRecordService;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 专区标签批量操作记录：批次落库、操作记录分页/详情、按 before 快照撤销。
 *
 * <p>撤销冲突判定（契约 §2.7）：批次明细 before/after JSON 使用
 * {@link ZoneTagPersistSnapshot}（SPU rule + 全量 SKU 覆盖原值）。对每条实际变更项，
 * 先比较「当前 SPU 8 个专区字段 + 当前 SKU 8 个覆盖字段」是否仍等于该批次 after 值：
 * 相等才按 before 回写；不相等说明批次之后被人工改过，跳过并计入 conflicts。
 *
 * <p>比较口径：BigDecimal 用 {@code compareTo}（DB 0.9000 vs JSON 0.9）；时间截断到秒；
 * 标签用 0/1 归一化（null 视为 0）。撤销只补写商品表中的专区字段，不删除/不动其它字段，
 * 本批次快照中未出现的 SKU 行保持不动。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneTagBatchRecordServiceImpl implements IZoneTagBatchRecordService {

	private static final long PAGE_DEFAULT_SIZE = 10L;

	private static final long PAGE_MAX_SIZE = 100L;

	private static final String UNDO_STATUS_NONE = "NONE";

	private static final String UNDO_STATUS_UNDONE = "UNDONE";

	private static final int SYNC_MESSAGE_MAX_LENGTH = 500;

	/** 只有实际发生了变化的条目才值得撤销；NO_CHANGE/INVALID/FAILED 跳过。 */
	private static final Set<String> UNDOABLE_CHANGE_TYPES = Set.of("ADD", "UPDATE", "REMOVE");

	private final GoodsZoneTagBatchMapper goodsZoneTagBatchMapper;

	private final GoodsZoneTagBatchItemMapper goodsZoneTagBatchItemMapper;

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final ZoneGoodsQueryMapper zoneGoodsQueryMapper;

	private final GoodsZoneTagStateResolver zoneTagStateResolver;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveBatch(GoodsZoneTagBatch batch, List<GoodsZoneTagBatchItem> items) {
		if (batch == null) {
			throw new ArynBusinessException("批次信息不能为空");
		}
		LocalDateTime now = LocalDateTime.now();
		if (!StringUtils.hasText(batch.getId())) {
			batch.setId(IdUtil.getSnowflakeNextIdStr());
		}
		if (!StringUtils.hasText(batch.getTenantId())) {
			batch.setTenantId(ArynTenantContextHolder.getTenantId());
		}
		if (!StringUtils.hasText(batch.getDelFlag())) {
			batch.setDelFlag("0");
		}
		if (!StringUtils.hasText(batch.getUndoStatus())) {
			batch.setUndoStatus(UNDO_STATUS_NONE);
		}
		if (batch.getCreateTime() == null) {
			batch.setCreateTime(now);
		}
		goodsZoneTagBatchMapper.insert(batch);
		if (CollectionUtils.isEmpty(items)) {
			return;
		}
		for (GoodsZoneTagBatchItem item : items) {
			if (item == null) {
				continue;
			}
			item.setBatchId(batch.getId());
			if (!StringUtils.hasText(item.getTenantId())) {
				item.setTenantId(batch.getTenantId());
			}
			if (!StringUtils.hasText(item.getDelFlag())) {
				item.setDelFlag("0");
			}
			if (item.getCreateTime() == null) {
				item.setCreateTime(now);
			}
			goodsZoneTagBatchItemMapper.insert(item);
		}
	}

	@Override
	public void updateSyncStatus(String batchId, String syncStatus, String syncMessage) {
		if (!StringUtils.hasText(batchId)) {
			throw new ArynBusinessException("批次ID不能为空");
		}
		int updated = goodsZoneTagBatchMapper.update(null, Wrappers.<GoodsZoneTagBatch>lambdaUpdate()
			.eq(GoodsZoneTagBatch::getId, batchId)
			.set(GoodsZoneTagBatch::getSyncStatus, StringUtils.hasText(syncStatus) ? syncStatus : "SKIPPED")
			.set(GoodsZoneTagBatch::getSyncMessage, truncate(syncMessage, SYNC_MESSAGE_MAX_LENGTH)));
		if (updated == 0) {
			log.warn("回写批次同步状态未命中 batchId={} status={}", batchId, syncStatus);
		}
	}

	@Override
	public IPage<ZoneTagBatchVO> page(long current, long size) {
		long safeCurrent = current <= 0 ? 1L : current;
		long safeSize = size <= 0 ? PAGE_DEFAULT_SIZE : Math.min(size, PAGE_MAX_SIZE);
		IPage<GoodsZoneTagBatch> batchPage = goodsZoneTagBatchMapper.selectPage(new Page<>(safeCurrent, safeSize),
				Wrappers.<GoodsZoneTagBatch>lambdaQuery()
					.orderByDesc(GoodsZoneTagBatch::getCreateTime)
					.orderByDesc(GoodsZoneTagBatch::getId));
		Page<ZoneTagBatchVO> result = new Page<>(batchPage.getCurrent(), batchPage.getSize(),
				batchPage.getTotal());
		List<ZoneTagBatchVO> records = new ArrayList<>(batchPage.getRecords().size());
		for (GoodsZoneTagBatch batch : batchPage.getRecords()) {
			ZoneTagBatchVO vo = new ZoneTagBatchVO();
			fillBatchVO(vo, batch);
			records.add(vo);
		}
		result.setRecords(records);
		return result;
	}

	@Override
	public ZoneTagBatchDetailVO detail(String batchId) {
		GoodsZoneTagBatch batch = requireBatch(batchId);
		ZoneTagBatchDetailVO detail = new ZoneTagBatchDetailVO();
		fillBatchVO(detail, batch);
		List<GoodsZoneTagBatchItem> items = goodsZoneTagBatchItemMapper.selectList(
				Wrappers.<GoodsZoneTagBatchItem>lambdaQuery()
					.eq(GoodsZoneTagBatchItem::getBatchId, batch.getId())
					.orderByAsc(GoodsZoneTagBatchItem::getCreateTime)
					.orderByAsc(GoodsZoneTagBatchItem::getId));
		List<ZoneTagBatchItemVO> itemVOs = new ArrayList<>(items.size());
		for (GoodsZoneTagBatchItem item : items) {
			itemVOs.add(toItemVO(item));
		}
		detail.setItems(itemVOs);
		return detail;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ZoneTagUndoResultVO undo(String batchId) {
		GoodsZoneTagBatch batch = requireBatch(batchId);
		if (UNDO_STATUS_UNDONE.equals(batch.getUndoStatus())) {
			throw new ArynBusinessException("该批次已撤销，不能重复撤销");
		}
		List<GoodsZoneTagBatchItem> items = goodsZoneTagBatchItemMapper.selectList(
				Wrappers.<GoodsZoneTagBatchItem>lambdaQuery()
					.eq(GoodsZoneTagBatchItem::getBatchId, batch.getId())
					.orderByAsc(GoodsZoneTagBatchItem::getId));
		List<GoodsZoneTagBatchItem> undoItems = new ArrayList<>();
		if (!CollectionUtils.isEmpty(items)) {
			for (GoodsZoneTagBatchItem item : items) {
				if (isUndoable(item)) {
					undoItems.add(item);
				}
			}
		}
		ZoneTagUndoResultVO result = new ZoneTagUndoResultVO();
		result.setBatchId(batch.getId());
		result.setRestored(0);
		result.setSkipped(0);
		result.setConflicts(new ArrayList<>());
		if (undoItems.isEmpty()) {
			markUndone(batch.getId(), LocalDateTime.now());
			return result;
		}
		List<String> spuIds = undoItems.stream()
			.map(GoodsZoneTagBatchItem::getSpuId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, GoodsSpu> spuMap = loadSpuMap(spuIds);
		Map<String, List<GoodsSku>> skuMap = loadSkuMap(spuIds);
		LocalDateTime now = LocalDateTime.now();
		int restored = 0;
		List<ZoneTagUndoResultVO.Conflict> conflicts = new ArrayList<>();
		for (GoodsZoneTagBatchItem item : undoItems) {
			String spuId = item.getSpuId();
			GoodsSpu currentSpu = spuMap.get(spuId);
			if (currentSpu == null) {
				conflicts.add(conflict(spuId, item.getSpuName(), "商品已删除或不存在"));
				continue;
			}
			ZoneTagPersistSnapshot before = parseSnapshot(item.getBeforeSnapshot());
			ZoneTagPersistSnapshot after = parseSnapshot(item.getAfterSnapshot());
			if (before == null || before.getRule() == null) {
				conflicts.add(conflict(spuId, item.getSpuName(), "批次快照缺失，无法撤销"));
				continue;
			}
			List<GoodsSku> currentSkus = skuMap.getOrDefault(spuId, List.of());
			String conflictReason = detectConflict(currentSpu, currentSkus, after);
			if (conflictReason != null) {
				conflicts.add(conflict(spuId, item.getSpuName(), conflictReason));
				continue;
			}
			restoreSpu(spuId, before.getRule(), now);
			restoreSkus(spuId, before.getSkus(), currentSkus, after, now);
			restored++;
		}
		markUndone(batch.getId(), now);
		result.setRestored(restored);
		result.setSkipped(conflicts.size());
		result.setConflicts(conflicts);
		return result;
	}

	/**
	 * 正常路径回写批次头撤销状态。
	 */
	private void markUndone(String batchId, LocalDateTime now) {
		int updated = goodsZoneTagBatchMapper.update(null, Wrappers.<GoodsZoneTagBatch>lambdaUpdate()
			.eq(GoodsZoneTagBatch::getId, batchId)
			.ne(GoodsZoneTagBatch::getUndoStatus, UNDO_STATUS_UNDONE)
			.set(GoodsZoneTagBatch::getUndoStatus, UNDO_STATUS_UNDONE)
			.set(GoodsZoneTagBatch::getUndoTime, now));
		if (updated == 0) {
			// 并发撤销：另一个请求已把批次置为 UNDONE，本请求回滚已恢复的数据（事务保证）
			throw new ArynBusinessException("该批次已撤销或撤销状态已变化，请刷新后重试");
		}
	}

	private boolean isUndoable(GoodsZoneTagBatchItem item) {
		if (item == null || !Boolean.TRUE.equals(item.getSuccess()) || !StringUtils.hasText(item.getSpuId())) {
			return false;
		}
		if (!UNDOABLE_CHANGE_TYPES.contains(item.getChangeType())) {
			return false;
		}
		return StringUtils.hasText(item.getBeforeSnapshot()) && StringUtils.hasText(item.getAfterSnapshot());
	}

	/**
	 * 冲突判定：当前值与批次 after 快照不一致时返回原因；一致返回 null。
	 *
	 * <p>只比较专区字段：SPU 的 8 个专区字段 + after 快照中每个 SKU 的 8 个覆盖字段
	 * （普通售价/规格等变化不影响撤销语义）。SKU 不存在视为冲突。
	 */
	private String detectConflict(GoodsSpu currentSpu, List<GoodsSku> currentSkus,
			ZoneTagPersistSnapshot after) {
		if (after == null || after.getRule() == null) {
			// after 缺失（INVALID/FAILED 已被过滤），无可校验内容
			return null;
		}
		ZoneRuleSnapshotVO currentRule = zoneTagStateResolver.snapshot(currentSpu, currentSkus);
		if (!sameRuleZoneFields(currentRule, after.getRule())) {
			return "SPU 专区字段已被后续修改";
		}
		if (CollectionUtils.isEmpty(after.getSkus())) {
			return null;
		}
		Map<String, GoodsSku> currentSkuMap = new HashMap<>();
		for (GoodsSku sku : currentSkus) {
			if (sku != null && StringUtils.hasText(sku.getId())) {
				currentSkuMap.put(sku.getId(), sku);
			}
		}
		for (ZoneSkuStateVO afterSku : after.getSkus()) {
			if (afterSku == null || !StringUtils.hasText(afterSku.getSkuId())) {
				continue;
			}
			GoodsSku currentSku = currentSkuMap.get(afterSku.getSkuId());
			if (currentSku == null) {
				return "SKU 已删除或不存在：" + afterSku.getSkuId();
			}
			ZoneSkuStateVO currentSkuState = zoneTagStateResolver.toSkuState(currentSku);
			if (!sameSkuZoneFields(currentSkuState, afterSku)) {
				return "SKU 覆盖值已被后续修改：" + afterSku.getSkuId();
			}
		}
		return null;
	}

	private boolean sameRuleZoneFields(ZoneRuleSnapshotVO current, ZoneRuleSnapshotVO expected) {
		return Objects.equals(GoodsZoneTagStateResolver.tagValue(current.getMemberTag()),
				GoodsZoneTagStateResolver.tagValue(expected.getMemberTag()))
				&& sameDecimal(current.getMemberDiscountRate(), expected.getMemberDiscountRate())
				&& sameDecimal(current.getMemberPriceFixed(), expected.getMemberPriceFixed())
				&& sameDecimal(current.getMemberPriceFloor(), expected.getMemberPriceFloor())
				&& Objects.equals(GoodsZoneTagStateResolver.tagValue(current.getClearanceTag()),
						GoodsZoneTagStateResolver.tagValue(expected.getClearanceTag()))
				&& sameDecimal(current.getClearancePrice(), expected.getClearancePrice())
				&& sameDecimal(current.getClearanceDiscountRate(), expected.getClearanceDiscountRate())
				&& sameTime(current.getClearanceExpireTime(), expected.getClearanceExpireTime());
	}

	private boolean sameSkuZoneFields(ZoneSkuStateVO current, ZoneSkuStateVO expected) {
		return Objects.equals(current.getIsMemberItem(), expected.getIsMemberItem())
				&& sameDecimal(current.getMemberDiscountRate(), expected.getMemberDiscountRate())
				&& sameDecimal(current.getMemberPriceFixed(), expected.getMemberPriceFixed())
				&& sameDecimal(current.getMemberPriceFloor(), expected.getMemberPriceFloor())
				&& Objects.equals(current.getIsClearanceItem(), expected.getIsClearanceItem())
				&& sameDecimal(current.getClearancePrice(), expected.getClearancePrice())
				&& sameDecimal(current.getClearanceDiscountRate(), expected.getClearanceDiscountRate())
				&& sameTime(current.getClearanceExpireTime(), expected.getClearanceExpireTime());
	}

	private boolean sameDecimal(BigDecimal left, BigDecimal right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.compareTo(right) == 0;
	}

	private boolean sameTime(LocalDateTime left, LocalDateTime right) {
		if (left == null || right == null) {
			return left == right;
		}
		return left.truncatedTo(ChronoUnit.SECONDS).equals(right.truncatedTo(ChronoUnit.SECONDS));
	}

	/**
	 * 回写 SPU 的 8 个专区字段（含显式 null，恢复「未配置」语义）。
	 */
	private void restoreSpu(String spuId, ZoneRuleSnapshotVO beforeRule, LocalDateTime now) {
		goodsSpuMapper.update(null, Wrappers.<GoodsSpu>lambdaUpdate()
			.eq(GoodsSpu::getId, spuId)
			.set(GoodsSpu::getIsMemberItem, GoodsZoneTagStateResolver.tagValue(beforeRule.getMemberTag()))
			.set(GoodsSpu::getMemberDiscountRate, beforeRule.getMemberDiscountRate())
			.set(GoodsSpu::getMemberPriceFixed, beforeRule.getMemberPriceFixed())
			.set(GoodsSpu::getMemberPriceFloor, beforeRule.getMemberPriceFloor())
			.set(GoodsSpu::getIsClearanceItem, GoodsZoneTagStateResolver.tagValue(beforeRule.getClearanceTag()))
			.set(GoodsSpu::getClearancePrice, beforeRule.getClearancePrice())
			.set(GoodsSpu::getClearanceDiscountRate, beforeRule.getClearanceDiscountRate())
			.set(GoodsSpu::getClearanceExpireTime, beforeRule.getClearanceExpireTime())
			.set(GoodsSpu::getUpdateTime, now));
	}

	/**
	 * 回写本批次涉及 SKU 的 8 个覆盖字段（null=恢复继承；未涉及 SKU 不动）。
	 */
	private void restoreSkus(String spuId, List<ZoneSkuStateVO> beforeSkus, List<GoodsSku> currentSkus,
			ZoneTagPersistSnapshot after, LocalDateTime now) {
		if (CollectionUtils.isEmpty(beforeSkus)) {
			return;
		}
		Set<String> verifiedSkuIds = new HashSet<>();
		if (after != null && after.getSkus() != null) {
			for (ZoneSkuStateVO afterSku : after.getSkus()) {
				if (afterSku != null && StringUtils.hasText(afterSku.getSkuId())) {
					verifiedSkuIds.add(afterSku.getSkuId());
				}
			}
		}
		Map<String, String> currentSkuIds = new HashMap<>();
		for (GoodsSku sku : currentSkus) {
			if (sku != null && StringUtils.hasText(sku.getId())) {
				currentSkuIds.put(sku.getId(), sku.getSpuId());
			}
		}
		for (ZoneSkuStateVO beforeSku : beforeSkus) {
			if (beforeSku == null || !StringUtils.hasText(beforeSku.getSkuId())) {
				continue;
			}
			String skuId = beforeSku.getSkuId();
			if (!verifiedSkuIds.contains(skuId) || !currentSkuIds.containsKey(skuId)) {
				continue;
			}
			goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
				.eq(GoodsSku::getId, skuId)
				.eq(GoodsSku::getSpuId, spuId)
				.set(GoodsSku::getIsMemberItem, beforeSku.getIsMemberItem())
				.set(GoodsSku::getMemberDiscountRate, beforeSku.getMemberDiscountRate())
				.set(GoodsSku::getMemberPriceFixed, beforeSku.getMemberPriceFixed())
				.set(GoodsSku::getMemberPriceFloor, beforeSku.getMemberPriceFloor())
				.set(GoodsSku::getIsClearanceItem, beforeSku.getIsClearanceItem())
				.set(GoodsSku::getClearancePrice, beforeSku.getClearancePrice())
				.set(GoodsSku::getClearanceDiscountRate, beforeSku.getClearanceDiscountRate())
				.set(GoodsSku::getClearanceExpireTime, beforeSku.getClearanceExpireTime())
				.set(GoodsSku::getUpdateTime, now));
		}
	}

	private GoodsZoneTagBatch requireBatch(String batchId) {
		if (!StringUtils.hasText(batchId)) {
			throw new ArynBusinessException("批次ID不能为空");
		}
		GoodsZoneTagBatch batch = goodsZoneTagBatchMapper.selectById(batchId);
		if (batch == null) {
			throw new ArynBusinessException("批次不存在");
		}
		return batch;
	}

	private Map<String, GoodsSpu> loadSpuMap(List<String> spuIds) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return Map.of();
		}
		List<GoodsSpu> spus = goodsSpuMapper.selectBatchIds(spuIds);
		Map<String, GoodsSpu> map = new HashMap<>();
		if (spus != null) {
			for (GoodsSpu spu : spus) {
				if (spu != null && StringUtils.hasText(spu.getId())) {
					map.put(spu.getId(), spu);
				}
			}
		}
		return map;
	}

	private Map<String, List<GoodsSku>> loadSkuMap(List<String> spuIds) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return Map.of();
		}
		List<GoodsSku> skus = zoneGoodsQueryMapper.selectSkuZoneRows(spuIds);
		if (CollectionUtils.isEmpty(skus)) {
			return Map.of();
		}
		Map<String, List<GoodsSku>> map = new HashMap<>();
		for (GoodsSku sku : skus) {
			if (sku != null && StringUtils.hasText(sku.getSpuId())) {
				map.computeIfAbsent(sku.getSpuId(), key -> new ArrayList<>()).add(sku);
			}
		}
		return map;
	}

	private ZoneTagPersistSnapshot parseSnapshot(String json) {
		if (!StringUtils.hasText(json)) {
			return null;
		}
		try {
			return JSONUtil.toBean(json, new TypeReference<ZoneTagPersistSnapshot>() {
			}, false);
		}
		catch (Exception ex) {
			log.warn("解析批次明细快照失败，按缺失处理 json={}", json, ex);
			return null;
		}
	}

	private ZoneTagBatchItemVO toItemVO(GoodsZoneTagBatchItem item) {
		ZoneTagBatchItemVO vo = new ZoneTagBatchItemVO();
		vo.setSpuId(item.getSpuId());
		vo.setSpuName(item.getSpuName());
		vo.setSuccess(item.getSuccess());
		vo.setChangeType(item.getChangeType());
		vo.setMessage(item.getMessage());
		vo.setErrorCode(item.getErrorCode());
		vo.setChanges(buildChanges(item));
		return vo;
	}

	/**
	 * 从 before/after 快照还原分专区变更描述（详情接口用；执行时的 warnings 未持久化）。
	 */
	private List<ZoneTagChangeVO> buildChanges(GoodsZoneTagBatchItem item) {
		ZoneTagPersistSnapshot before = parseSnapshot(item.getBeforeSnapshot());
		ZoneTagPersistSnapshot after = parseSnapshot(item.getAfterSnapshot());
		if (before == null || after == null || before.getRule() == null || after.getRule() == null) {
			return List.of();
		}
		List<ZoneTagChangeVO> changes = new ArrayList<>();
		addZoneChange(changes, GoodsZoneTagStateResolver.ZONE_MEMBER, before.getRule(), after.getRule());
		addZoneChange(changes, GoodsZoneTagStateResolver.ZONE_CLEARANCE, before.getRule(), after.getRule());
		return changes;
	}

	private void addZoneChange(List<ZoneTagChangeVO> changes, String zone, ZoneRuleSnapshotVO before,
			ZoneRuleSnapshotVO after) {
		String type = changeTypeOf(zone, before, after);
		if ("NO_CHANGE".equals(type)) {
			return;
		}
		ZoneTagChangeVO change = new ZoneTagChangeVO();
		change.setZone(zone);
		change.setType(type);
		change.setBefore(ruleSnapshotMap(zone, before));
		change.setAfter(ruleSnapshotMap(zone, after));
		changes.add(change);
	}

	private String changeTypeOf(String zone, ZoneRuleSnapshotVO before, ZoneRuleSnapshotVO after) {
		int beforeTag;
		int afterTag;
		boolean rulesDiffer;
		if (GoodsZoneTagStateResolver.ZONE_MEMBER.equals(zone)) {
			beforeTag = GoodsZoneTagStateResolver.tagValue(before.getMemberTag());
			afterTag = GoodsZoneTagStateResolver.tagValue(after.getMemberTag());
			rulesDiffer = !sameDecimal(before.getMemberDiscountRate(), after.getMemberDiscountRate())
					|| !sameDecimal(before.getMemberPriceFixed(), after.getMemberPriceFixed())
					|| !sameDecimal(before.getMemberPriceFloor(), after.getMemberPriceFloor());
		}
		else {
			beforeTag = GoodsZoneTagStateResolver.tagValue(before.getClearanceTag());
			afterTag = GoodsZoneTagStateResolver.tagValue(after.getClearanceTag());
			rulesDiffer = !sameDecimal(before.getClearancePrice(), after.getClearancePrice())
					|| !sameDecimal(before.getClearanceDiscountRate(), after.getClearanceDiscountRate())
					|| !sameTime(before.getClearanceExpireTime(), after.getClearanceExpireTime());
		}
		if (beforeTag != 1 && afterTag == 1) {
			return "ADD";
		}
		if (beforeTag == 1 && afterTag != 1) {
			return "REMOVE";
		}
		return rulesDiffer ? "UPDATE" : "NO_CHANGE";
	}

	private Map<String, Object> ruleSnapshotMap(String zone, ZoneRuleSnapshotVO rule) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("zone", zone);
		if (GoodsZoneTagStateResolver.ZONE_MEMBER.equals(zone)) {
			map.put("memberTag", GoodsZoneTagStateResolver.tagValue(rule.getMemberTag()));
			map.put("memberDiscountRate", rule.getMemberDiscountRate());
			map.put("memberPriceFixed", rule.getMemberPriceFixed());
			map.put("memberPriceFloor", rule.getMemberPriceFloor());
		}
		else {
			map.put("clearanceTag", GoodsZoneTagStateResolver.tagValue(rule.getClearanceTag()));
			map.put("clearancePrice", rule.getClearancePrice());
			map.put("clearanceDiscountRate", rule.getClearanceDiscountRate());
			map.put("clearanceExpireTime", rule.getClearanceExpireTime());
		}
		return map;
	}

	private void fillBatchVO(ZoneTagBatchVO vo, GoodsZoneTagBatch batch) {
		vo.setBatchId(batch.getId());
		vo.setBatchNo(batch.getBatchNo());
		vo.setOperatorId(batch.getOperatorId());
		vo.setOperatorName(batch.getOperatorName());
		vo.setCreateTime(batch.getCreateTime());
		vo.setTotal(batch.getTotal());
		vo.setChanged(batch.getChanged());
		vo.setAdded(batch.getAdded());
		vo.setUpdated(batch.getUpdated());
		vo.setRemoved(batch.getRemoved());
		vo.setNoChange(batch.getNoChange());
		vo.setFailed(batch.getFailed());
		vo.setMemberAction(batch.getMemberAction());
		vo.setClearanceAction(batch.getClearanceAction());
		vo.setSyncStatus(batch.getSyncStatus());
		vo.setSyncMessage(batch.getSyncMessage());
		vo.setUndoStatus(batch.getUndoStatus());
		vo.setUndoTime(batch.getUndoTime());
	}

	private ZoneTagUndoResultVO.Conflict conflict(String spuId, String spuName, String reason) {
		return new ZoneTagUndoResultVO.Conflict(spuId, spuName, reason);
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

}
