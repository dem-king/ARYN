package com.aryn.cloud.product.service.support;

import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.ZoneRuleSnapshotVO;
import com.aryn.cloud.product.api.vo.ZoneSkuStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagChangeVO;
import com.aryn.cloud.product.api.vo.ZoneTagItemStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagWarningVO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 专区标签状态解析：快照、拟变更判定、告警、生效状态。
 *
 * <p>预检与执行共用本类，保证「预检影响 = 实际执行结果」；所有判定只做静态推算，
 * 最终 C 端价格以促销域同步结果为准。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Component
public class GoodsZoneTagStateResolver {

	public static final String ZONE_MEMBER = "member";

	public static final String ZONE_CLEARANCE = "clearance";

	/** 生效状态。 */
	public record EffectiveZone(String state, String stateLabel, BigDecimal effectivePrice) {
	}

	public ZoneRuleSnapshotVO snapshot(GoodsSpu spu, List<GoodsSku> skus) {
		ZoneRuleSnapshotVO snapshot = new ZoneRuleSnapshotVO();
		snapshot.setMemberTag(tagValue(spu.getIsMemberItem()));
		snapshot.setMemberDiscountRate(spu.getMemberDiscountRate());
		snapshot.setMemberPriceFixed(spu.getMemberPriceFixed());
		snapshot.setMemberPriceFloor(spu.getMemberPriceFloor());
		snapshot.setClearanceTag(tagValue(spu.getIsClearanceItem()));
		snapshot.setClearancePrice(spu.getClearancePrice());
		snapshot.setClearanceDiscountRate(spu.getClearanceDiscountRate());
		snapshot.setClearanceExpireTime(spu.getClearanceExpireTime());
		snapshot.setSkuOverrideCount(skuOverrideCount(skus));
		return snapshot;
	}

	public int skuOverrideCount(List<GoodsSku> skus) {
		if (CollectionUtils.isEmpty(skus)) {
			return 0;
		}
		return (int) skus.stream().filter(this::hasZoneOverride).count();
	}

	public int skuTagExclusionCount(List<GoodsSku> skus, String zone) {
		if (CollectionUtils.isEmpty(skus)) {
			return 0;
		}
		if (ZONE_MEMBER.equals(zone)) {
			return (int) skus.stream().filter(sku -> Objects.equals(sku.getIsMemberItem(), 0)).count();
		}
		return (int) skus.stream().filter(sku -> Objects.equals(sku.getIsClearanceItem(), 0)).count();
	}

	/** 商品级告警（与专区无关）。 */
	public List<ZoneTagWarningVO> itemWarnings(GoodsSpu spu, boolean recentSalesIncrease) {
		List<ZoneTagWarningVO> warnings = new ArrayList<>();
		if (!"1".equals(spu.getStatus())) {
			warnings.add(warning("OFF_SHELF", "商品已下架，打标后 C 端不展示"));
		}
		if (recentSalesIncrease) {
			warnings.add(warning("RECENT_SALES_INCREASE", "最近 30 天售价上调过，请确认出清价真实优惠"));
		}
		return warnings;
	}

	/**
	 * 构造预检单项：当前快照 + 每个专区的拟变更与告警。
	 */
	public ZoneTagItemStateVO previewItem(GoodsSpu spu, List<GoodsSku> skus, GoodsZoneTagBatchDTO dto,
			BigDecimal memberDefaultRate, BigDecimal clearanceDefaultRate, boolean recentSalesIncrease) {
		ZoneTagItemStateVO item = new ZoneTagItemStateVO();
		item.setSpuId(spu.getId());
		item.setSpuName(spu.getName());
		item.setSpuStatus(spu.getStatus());
		item.setBefore(snapshot(spu, skus));
		List<ZoneTagChangeVO> changes = new ArrayList<>();
		changes.add(evaluateMember(spu, skus, dto, memberDefaultRate));
		changes.add(evaluateClearance(spu, skus, dto, clearanceDefaultRate));
		item.setChanges(changes);
		item.setWarnings(itemWarnings(spu, recentSalesIncrease));
		return item;
	}

	public ZoneTagChangeVO evaluateMember(GoodsSpu spu, List<GoodsSku> skus, GoodsZoneTagBatchDTO dto,
			BigDecimal zoneDefaultRate) {
		ZoneTagChangeVO change = new ZoneTagChangeVO();
		change.setZone(ZONE_MEMBER);
		List<ZoneTagWarningVO> warnings = new ArrayList<>();
		change.setWarnings(warnings);
		Boolean tag = dto.getMemberTag();
		if (tag == null) {
			change.setType("NO_CHANGE");
			return change;
		}
		boolean current = Objects.equals(spu.getIsMemberItem(), 1);
		if (!tag) {
			change.setType(current ? "REMOVE" : "NO_CHANGE");
			return change;
		}
		boolean ruleProvided = dto.getMemberDiscountRate() != null || dto.getMemberPriceFixed() != null;
		int skuOverrides = skuOverrideCount(skus);
		int explicitExclusions = skuTagExclusionCount(skus, ZONE_MEMBER);
		boolean changed = !current
				|| (ruleProvided && (differs(spu.getMemberDiscountRate(), dto.getMemberDiscountRate())
						|| differs(spu.getMemberPriceFixed(), dto.getMemberPriceFixed())))
				|| (Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && explicitExclusions > 0);
		change.setType(changed ? (current ? "UPDATE" : "ADD") : "NO_CHANGE");
		if (!ruleProvided) {
			if (zoneDefaultRate != null && zoneDefaultRate.signum() > 0) {
				warnings.add(warning("ZONE_DEFAULT_RATE",
						"未配置商品会员规则，将使用专区默认折扣率 " + zoneDefaultRate.stripTrailingZeros().toPlainString()));
			}
			else {
				warnings.add(warning("NO_RULE", "未配置会员折扣率/固定价，会员价可能不生效（按等级折扣兜底）"));
			}
		}
		else if (Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && skuOverrides > 0) {
			warnings.add(warning("SKU_OVERRIDE_CLEARED", skuOverrides + " 个 SKU 的精调将被清除，统一按本次规则生效"));
		}
		else if (!Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && skuOverrides > 0) {
			warnings.add(warning("SKU_OVERRIDE_PRESERVED", skuOverrides + " 个 SKU 将保留已有精调"));
		}
		BigDecimal afterRate = dto.getMemberDiscountRate() != null ? dto.getMemberDiscountRate()
				: firstNonNull(spu.getMemberDiscountRate(), zoneDefaultRate);
		BigDecimal afterFixed = dto.getMemberPriceFixed() != null ? dto.getMemberPriceFixed()
				: spu.getMemberPriceFixed();
		BigDecimal price = memberPrice(spu.getSalesPrice(), afterRate, afterFixed);
		if (price != null && spu.getSalesPrice() != null && price.compareTo(spu.getSalesPrice()) >= 0) {
			warnings.add(warning("INVERTED_PRICE", "按当前规则算出的会员价不低于售价，会员价将不生效"));
		}
		return change;
	}

	public ZoneTagChangeVO evaluateClearance(GoodsSpu spu, List<GoodsSku> skus, GoodsZoneTagBatchDTO dto,
			BigDecimal zoneDefaultRate) {
		ZoneTagChangeVO change = new ZoneTagChangeVO();
		change.setZone(ZONE_CLEARANCE);
		List<ZoneTagWarningVO> warnings = new ArrayList<>();
		change.setWarnings(warnings);
		Boolean tag = dto.getClearanceTag();
		if (tag == null) {
			change.setType("NO_CHANGE");
			return change;
		}
		boolean current = Objects.equals(spu.getIsClearanceItem(), 1);
		if (!tag) {
			change.setType(current ? "REMOVE" : "NO_CHANGE");
			return change;
		}
		boolean ruleProvided = dto.getClearancePrice() != null || dto.getClearanceDiscountRate() != null
				|| dto.getClearanceExpireTime() != null;
		BigDecimal afterPrice = dto.getClearancePrice() != null ? dto.getClearancePrice() : spu.getClearancePrice();
		BigDecimal afterRate = dto.getClearanceDiscountRate() != null ? dto.getClearanceDiscountRate()
				: spu.getClearanceDiscountRate();
		LocalDateTime afterExpire = dto.getClearanceExpireTime() != null ? dto.getClearanceExpireTime()
				: spu.getClearanceExpireTime();
		if (!GoodsZoneTagRuleValidator.clearanceRuleAvailable(afterPrice, afterRate, zoneDefaultRate)) {
			change.setType("INVALID");
			warnings.add(warning("NO_RULE", "出清标签需要商品出清价/折扣率或专区默认折扣，否则打标后不生效"));
			return change;
		}
		int skuOverrides = skuOverrideCount(skus);
		int explicitExclusions = skuTagExclusionCount(skus, ZONE_CLEARANCE);
		boolean changed = !current
				|| (ruleProvided && (differs(spu.getClearancePrice(), dto.getClearancePrice())
						|| differs(spu.getClearanceDiscountRate(), dto.getClearanceDiscountRate())
						|| !Objects.equals(spu.getClearanceExpireTime(), dto.getClearanceExpireTime())))
				|| (Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && explicitExclusions > 0);
		change.setType(changed ? (current ? "UPDATE" : "ADD") : "NO_CHANGE");
		if (afterExpire != null && !afterExpire.isAfter(LocalDateTime.now())) {
			warnings.add(warning("EXPIRED", "出清截止时间已过，标签保留但专区价不会生效，请重新设置截止时间"));
		}
		if (!ruleProvided) {
			BigDecimal defaultRate = firstNonNull(afterRate, zoneDefaultRate);
			if (afterPrice == null && defaultRate != null) {
				warnings.add(warning("ZONE_DEFAULT_RATE",
						"未配置商品出清规则，将使用专区默认折扣率 " + defaultRate.stripTrailingZeros().toPlainString()));
			}
		}
		else if (Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && skuOverrides > 0) {
			warnings.add(warning("SKU_OVERRIDE_CLEARED", skuOverrides + " 个 SKU 的精调将被清除，统一按本次规则生效"));
		}
		else if (!Boolean.TRUE.equals(dto.getOverwriteSkuRules()) && skuOverrides > 0) {
			warnings.add(warning("SKU_OVERRIDE_PRESERVED", skuOverrides + " 个 SKU 将保留已有精调"));
		}
		BigDecimal price = clearancePrice(spu.getOriginalPrice(), afterPrice, firstNonNull(afterRate, zoneDefaultRate));
		if (price != null && spu.getSalesPrice() != null && price.compareTo(spu.getSalesPrice()) >= 0) {
			warnings.add(warning("INVERTED_PRICE", "按当前规则算出的出清价不低于售价，专区价将不生效"));
		}
		return change;
	}

	/**
	 * 管理端专区商品行的生效状态（SPU 级静态推算）。
	 */
	public EffectiveZone effective(GoodsSpu spu, String zoneType, BigDecimal memberDefaultRate,
			BigDecimal clearanceDefaultRate) {
		if (!"1".equals(spu.getStatus())) {
			return new EffectiveZone("OFF_SHELF", "已下架", null);
		}
		if (ZONE_MEMBER.equals(zoneType)) {
			BigDecimal rate = firstNonNull(spu.getMemberDiscountRate(), memberDefaultRate);
			BigDecimal price = memberPrice(spu.getSalesPrice(), rate, spu.getMemberPriceFixed());
			if (price == null) {
				return new EffectiveZone("NO_RULE", "未配会员规则", null);
			}
			if (spu.getSalesPrice() != null && price.compareTo(spu.getSalesPrice()) >= 0) {
				return new EffectiveZone("INVERTED", "倒挂不生效", null);
			}
			return new EffectiveZone("NORMAL", "生效中", price);
		}
		BigDecimal price = clearancePrice(spu.getOriginalPrice(), spu.getClearancePrice(),
				firstNonNull(spu.getClearanceDiscountRate(), clearanceDefaultRate));
		if (price == null) {
			return new EffectiveZone("NO_RULE", "未配出清规则", null);
		}
		if (spu.getClearanceExpireTime() != null
				&& !spu.getClearanceExpireTime().isAfter(LocalDateTime.now())) {
			return new EffectiveZone("EXPIRED", "已到期", null);
		}
		if (spu.getSalesPrice() != null && price.compareTo(spu.getSalesPrice()) >= 0) {
			return new EffectiveZone("INVERTED", "倒挂不生效", null);
		}
		return new EffectiveZone("NORMAL", "生效中", price);
	}

	/** 变更判定：请求中是否指定了动作（任一专区）。 */
	public boolean noAction(GoodsZoneTagBatchDTO dto) {
		return dto.getMemberTag() == null && dto.getClearanceTag() == null;
	}

	public List<ZoneSkuStateVO> skuStates(List<GoodsSku> skus) {
		if (CollectionUtils.isEmpty(skus)) {
			return List.of();
		}
		return skus.stream().map(this::toSkuState).collect(Collectors.toList());
	}

	public ZoneSkuStateVO toSkuState(GoodsSku sku) {
		ZoneSkuStateVO vo = new ZoneSkuStateVO();
		vo.setSkuId(sku.getId());
		vo.setSpecsName(formatSpecs(sku));
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
		vo.setHasOverride(hasZoneOverride(sku));
		return vo;
	}

	public boolean hasZoneOverride(GoodsSku sku) {
		return sku.getIsMemberItem() != null
				|| sku.getMemberDiscountRate() != null
				|| sku.getMemberPriceFixed() != null
				|| sku.getMemberPriceFloor() != null
				|| sku.getIsClearanceItem() != null
				|| sku.getClearancePrice() != null
				|| sku.getClearanceDiscountRate() != null
				|| sku.getClearanceExpireTime() != null;
	}

	/** 基于 SKU 状态 VO 判断是否存在任一专区覆盖。 */
	public boolean hasZoneOverrideFromState(ZoneSkuStateVO sku) {
		return sku.getIsMemberItem() != null
				|| sku.getMemberDiscountRate() != null
				|| sku.getMemberPriceFixed() != null
				|| sku.getMemberPriceFloor() != null
				|| sku.getIsClearanceItem() != null
				|| sku.getClearancePrice() != null
				|| sku.getClearanceDiscountRate() != null
				|| sku.getClearanceExpireTime() != null;
	}

	public static Integer tagValue(Integer value) {
		return Objects.equals(value, 1) ? 1 : 0;
	}

	public static BigDecimal memberPrice(BigDecimal salesPrice, BigDecimal rate, BigDecimal fixedPrice) {
		if (salesPrice == null || salesPrice.signum() <= 0) {
			return null;
		}
		BigDecimal price = null;
		if (rate != null && rate.signum() > 0) {
			price = salesPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
		}
		if (fixedPrice != null && fixedPrice.signum() > 0) {
			price = price == null ? fixedPrice : price.min(fixedPrice);
		}
		return price;
	}

	public static BigDecimal clearancePrice(BigDecimal originalPrice, BigDecimal fixedPrice, BigDecimal rate) {
		if (fixedPrice != null && fixedPrice.signum() > 0) {
			return fixedPrice;
		}
		if (rate != null && rate.signum() > 0 && originalPrice != null && originalPrice.signum() > 0) {
			return originalPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
		}
		return null;
	}

	public static ZoneTagWarningVO warning(String code, String message) {
		return new ZoneTagWarningVO(code, message);
	}

	private boolean differs(BigDecimal stored, BigDecimal incoming) {
		if (incoming == null) {
			return false;
		}
		return stored == null || stored.compareTo(incoming) != 0;
	}

	private <T> T firstNonNull(T first, T second) {
		return first != null ? first : second;
	}

	private String formatSpecs(GoodsSku sku) {
		if (CollectionUtils.isEmpty(sku.getSpecsArr())) {
			return "";
		}
		return sku.getSpecsArr().stream()
			.filter(Objects::nonNull)
			.map(spec -> StringUtils.hasText(spec.getSpecsValueName()) ? spec.getSpecsValueName()
					: spec.getSpecsValueId())
			.filter(StringUtils::hasText)
			.collect(Collectors.joining(" / "));
	}

}
