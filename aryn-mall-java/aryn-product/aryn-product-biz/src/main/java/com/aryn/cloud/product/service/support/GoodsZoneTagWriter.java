package com.aryn.cloud.product.service.support;

import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.impl.GoodsPriceHistoryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 专区标签写入：SPU 标签/规则更新、SKU 覆盖重置/清空、价格历史落库。
 *
 * <p>批量写入只对「已判定发生变化」的商品调用；SKU 覆盖默认保留，只有
 * {@code overwriteSkuRules=true} 才重置为继承；移出专区时 SKU 写显式 0 并清空规则。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Component
@RequiredArgsConstructor
public class GoodsZoneTagWriter {

	private final GoodsSpuMapper goodsSpuMapper;

	private final GoodsSkuMapper goodsSkuMapper;

	private final GoodsPriceHistoryService goodsPriceHistoryService;

	private final GoodsZoneTagStateResolver stateResolver;

	/**
	 * 执行批量变更（仅传入需要变更的专区）。
	 *
	 * @return 变更后的 SPU 快照
	 */
	public ZoneRuleSnapshotVO applyBatch(GoodsSpu spu, List<GoodsSku> skus, GoodsZoneTagBatchDTO dto,
			boolean memberTouched, boolean clearanceTouched, String reason) {
		ZoneRuleSnapshotVO before = stateResolver.snapshot(spu, skus);
		int skuCount = CollectionUtils.isEmpty(skus) ? 0 : skus.size();
		var update = Wrappers.<GoodsSpu>lambdaUpdate().eq(GoodsSpu::getId, spu.getId());
		boolean any = false;
		if (memberTouched) {
			any = true;
			Boolean tag = dto.getMemberTag();
			if (Boolean.FALSE.equals(tag)) {
				update.set(GoodsSpu::getIsMemberItem, 0)
					.set(GoodsSpu::getMemberDiscountRate, null)
					.set(GoodsSpu::getMemberPriceFixed, null)
					.set(GoodsSpu::getMemberPriceFloor, null);
			}
			else {
				update.set(GoodsSpu::getIsMemberItem, 1)
					.set(dto.getMemberDiscountRate() != null, GoodsSpu::getMemberDiscountRate,
							dto.getMemberDiscountRate())
					.set(dto.getMemberPriceFixed() != null, GoodsSpu::getMemberPriceFixed,
							dto.getMemberPriceFixed());
			}
		}
		if (clearanceTouched) {
			any = true;
			Boolean tag = dto.getClearanceTag();
			if (Boolean.FALSE.equals(tag)) {
				update.set(GoodsSpu::getIsClearanceItem, 0)
					.set(GoodsSpu::getClearancePrice, null)
					.set(GoodsSpu::getClearanceDiscountRate, null)
					.set(GoodsSpu::getClearanceExpireTime, null);
			}
			else {
				update.set(GoodsSpu::getIsClearanceItem, 1)
					.set(dto.getClearancePrice() != null, GoodsSpu::getClearancePrice, dto.getClearancePrice())
					.set(dto.getClearanceDiscountRate() != null, GoodsSpu::getClearanceDiscountRate,
							dto.getClearanceDiscountRate())
					.set(dto.getClearanceExpireTime() != null, GoodsSpu::getClearanceExpireTime,
							dto.getClearanceExpireTime());
			}
		}
		if (any) {
			goodsSpuMapper.update(null, update);
		}
		boolean overwrite = Boolean.TRUE.equals(dto.getOverwriteSkuRules());
		if (memberTouched) {
			if (Boolean.FALSE.equals(dto.getMemberTag())) {
				untagSkuMember(spu.getId());
			}
			else if (Boolean.TRUE.equals(dto.getMemberTag()) && overwrite) {
				resetSkuMember(spu.getId());
			}
		}
		if (clearanceTouched) {
			if (Boolean.FALSE.equals(dto.getClearanceTag())) {
				untagSkuClearance(spu.getId());
			}
			else if (Boolean.TRUE.equals(dto.getClearanceTag()) && overwrite) {
				resetSkuClearance(spu.getId());
			}
		}
		ZoneRuleSnapshotVO after = buildAfterSnapshot(before, dto, memberTouched, clearanceTouched);
		int afterOverrideCount = before.getSkuOverrideCount();
		if (memberTouched && Boolean.FALSE.equals(dto.getMemberTag())) {
			afterOverrideCount = skuCount;
		}
		else if (clearanceTouched && Boolean.FALSE.equals(dto.getClearanceTag())) {
			afterOverrideCount = skuCount;
		}
		else if (overwrite && (Boolean.TRUE.equals(dto.getMemberTag())
				|| Boolean.TRUE.equals(dto.getClearanceTag()))) {
			afterOverrideCount = 0;
		}
		after.setSkuOverrideCount(afterOverrideCount);
		writeHistory(spu.getId(), before, after, memberTouched, clearanceTouched, reason);
		mutateSpu(spu, dto, memberTouched, clearanceTouched);
		return after;
	}

	/** 显式清空 SPU 会员规则（不动标签）。 */
	public void clearMemberRule(String spuId) {
		goodsSpuMapper.update(null, Wrappers.<GoodsSpu>lambdaUpdate()
			.eq(GoodsSpu::getId, spuId)
			.set(GoodsSpu::getMemberDiscountRate, null)
			.set(GoodsSpu::getMemberPriceFixed, null)
			.set(GoodsSpu::getMemberPriceFloor, null));
	}

	/** 显式清空 SPU 出清规则（不动标签）。 */
	public void clearClearanceRule(String spuId) {
		goodsSpuMapper.update(null, Wrappers.<GoodsSpu>lambdaUpdate()
			.eq(GoodsSpu::getId, spuId)
			.set(GoodsSpu::getClearancePrice, null)
			.set(GoodsSpu::getClearanceDiscountRate, null)
			.set(GoodsSpu::getClearanceExpireTime, null));
	}

	public void untagSkuMember(String spuId) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsMemberItem, 0)
			.set(GoodsSku::getMemberDiscountRate, null)
			.set(GoodsSku::getMemberPriceFixed, null)
			.set(GoodsSku::getMemberPriceFloor, null));
	}

	public void resetSkuMember(String spuId) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsMemberItem, null)
			.set(GoodsSku::getMemberDiscountRate, null)
			.set(GoodsSku::getMemberPriceFixed, null)
			.set(GoodsSku::getMemberPriceFloor, null));
	}

	public void untagSkuClearance(String spuId) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsClearanceItem, 0)
			.set(GoodsSku::getClearancePrice, null)
			.set(GoodsSku::getClearanceDiscountRate, null)
			.set(GoodsSku::getClearanceExpireTime, null));
	}

	public void resetSkuClearance(String spuId) {
		goodsSkuMapper.update(null, Wrappers.<GoodsSku>lambdaUpdate()
			.eq(GoodsSku::getSpuId, spuId)
			.set(GoodsSku::getIsClearanceItem, null)
			.set(GoodsSku::getClearancePrice, null)
			.set(GoodsSku::getClearanceDiscountRate, null)
			.set(GoodsSku::getClearanceExpireTime, null));
	}

	private ZoneRuleSnapshotVO buildAfterSnapshot(ZoneRuleSnapshotVO before, GoodsZoneTagBatchDTO dto,
			boolean memberTouched, boolean clearanceTouched) {
		ZoneRuleSnapshotVO after = new ZoneRuleSnapshotVO();
		after.setMemberTag(before.getMemberTag());
		after.setMemberDiscountRate(before.getMemberDiscountRate());
		after.setMemberPriceFixed(before.getMemberPriceFixed());
		after.setMemberPriceFloor(before.getMemberPriceFloor());
		after.setClearanceTag(before.getClearanceTag());
		after.setClearancePrice(before.getClearancePrice());
		after.setClearanceDiscountRate(before.getClearanceDiscountRate());
		after.setClearanceExpireTime(before.getClearanceExpireTime());
		after.setSkuOverrideCount(before.getSkuOverrideCount());
		if (memberTouched) {
			if (Boolean.FALSE.equals(dto.getMemberTag())) {
				after.setMemberTag(0);
				after.setMemberDiscountRate(null);
				after.setMemberPriceFixed(null);
				after.setMemberPriceFloor(null);
			}
			else {
				after.setMemberTag(1);
				if (dto.getMemberDiscountRate() != null) {
					after.setMemberDiscountRate(dto.getMemberDiscountRate());
				}
				if (dto.getMemberPriceFixed() != null) {
					after.setMemberPriceFixed(dto.getMemberPriceFixed());
				}
			}
		}
		if (clearanceTouched) {
			if (Boolean.FALSE.equals(dto.getClearanceTag())) {
				after.setClearanceTag(0);
				after.setClearancePrice(null);
				after.setClearanceDiscountRate(null);
				after.setClearanceExpireTime(null);
			}
			else {
				after.setClearanceTag(1);
				if (dto.getClearancePrice() != null) {
					after.setClearancePrice(dto.getClearancePrice());
				}
				if (dto.getClearanceDiscountRate() != null) {
					after.setClearanceDiscountRate(dto.getClearanceDiscountRate());
				}
				if (dto.getClearanceExpireTime() != null) {
					after.setClearanceExpireTime(dto.getClearanceExpireTime());
				}
			}
		}
		return after;
	}

	private void writeHistory(String spuId, ZoneRuleSnapshotVO before, ZoneRuleSnapshotVO after,
			boolean memberTouched, boolean clearanceTouched, String reason) {
		if (memberTouched) {
			goodsPriceHistoryService.recordIfChanged(spuId, null, "member_rate", before.getMemberDiscountRate(),
				after.getMemberDiscountRate(), "tag_save", reason);
			goodsPriceHistoryService.recordIfChanged(spuId, null, "member_fixed", before.getMemberPriceFixed(),
				after.getMemberPriceFixed(), "tag_save", reason);
			goodsPriceHistoryService.recordIfChanged(spuId, null, "member_floor", before.getMemberPriceFloor(),
				after.getMemberPriceFloor(), "tag_save", reason);
		}
		if (clearanceTouched) {
			goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance", before.getClearancePrice(),
				after.getClearancePrice(), "tag_save", reason);
			goodsPriceHistoryService.recordIfChanged(spuId, null, "clearance_rate",
				before.getClearanceDiscountRate(), after.getClearanceDiscountRate(), "tag_save", reason);
		}
	}

	private void mutateSpu(GoodsSpu spu, GoodsZoneTagBatchDTO dto, boolean memberTouched,
			boolean clearanceTouched) {
		if (memberTouched) {
			boolean tag = Boolean.TRUE.equals(dto.getMemberTag());
			spu.setIsMemberItem(tag ? 1 : 0);
			if (!tag) {
				spu.setMemberDiscountRate(null);
				spu.setMemberPriceFixed(null);
				spu.setMemberPriceFloor(null);
			}
			else {
				if (dto.getMemberDiscountRate() != null) {
					spu.setMemberDiscountRate(dto.getMemberDiscountRate());
				}
				if (dto.getMemberPriceFixed() != null) {
					spu.setMemberPriceFixed(dto.getMemberPriceFixed());
				}
			}
		}
		if (clearanceTouched) {
			boolean tag = Boolean.TRUE.equals(dto.getClearanceTag());
			spu.setIsClearanceItem(tag ? 1 : 0);
			if (!tag) {
				spu.setClearancePrice(null);
				spu.setClearanceDiscountRate(null);
				spu.setClearanceExpireTime(null);
			}
			else {
				if (dto.getClearancePrice() != null) {
					spu.setClearancePrice(dto.getClearancePrice());
				}
				if (dto.getClearanceDiscountRate() != null) {
					spu.setClearanceDiscountRate(dto.getClearanceDiscountRate());
				}
				if (dto.getClearanceExpireTime() != null) {
					spu.setClearanceExpireTime(dto.getClearanceExpireTime());
				}
			}
		}
	}

}
