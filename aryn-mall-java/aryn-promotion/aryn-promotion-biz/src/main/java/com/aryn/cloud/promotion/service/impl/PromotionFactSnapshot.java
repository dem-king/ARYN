package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import lombok.Builder;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 单次计价所需的事实；不持有 Mapper、RPC 或惰性查询回调。入参和出参均防御性复制。 */
@Builder(toBuilder = true)
public record PromotionFactSnapshot(
	PromotionContextDTO context,
	LocalDateTime asOf,
	List<PromotionActivity> activities,
	List<PricePolicy> policies,
	List<PricePolicyItem> policyItems,
	Map<String, BigDecimal> giftPrices,
	Set<String> frequentSkuIds,
	Map<String, MemberLevelSnapshotVO> memberLevels,
	Map<String, Boolean> firstOrderEligibility,
	Map<Integer, Boolean> newbieEligibility,
	Map<String, BigDecimal> monthlyUsage,
	Map<String, Long> userUsage,
	Map<String, Integer> giftUsage
) {
	public PromotionFactSnapshot {
		context = copyContext(context);
		Objects.requireNonNull(asOf, "计价时点不能为空");
		activities = copyList(activities, PromotionActivity.class);
		policies = copyList(policies, PricePolicy.class);
		policyItems = copyList(policyItems, PricePolicyItem.class);
		giftPrices = immutableMap(giftPrices);
		frequentSkuIds = frequentSkuIds == null ? Set.of() : Set.copyOf(frequentSkuIds);
		Map<String, MemberLevelSnapshotVO> levels = new LinkedHashMap<>();
		if (memberLevels != null) { memberLevels.forEach((key, value) -> levels.put(key, copyLevel(value))); }
		memberLevels = Collections.unmodifiableMap(levels);
		firstOrderEligibility = immutableMap(firstOrderEligibility);
		// 历史新人规则允许 registerWithinDays=null，不能用 Map.copyOf 拒绝空 key。
		newbieEligibility = immutableMap(newbieEligibility);
		monthlyUsage = immutableMap(monthlyUsage);
		userUsage = immutableMap(userUsage);
		giftUsage = immutableMap(giftUsage);
	}

	@Override public PromotionContextDTO context() { return copyContext(context); }
	@Override public List<PromotionActivity> activities() { return copyList(activities, PromotionActivity.class); }
	@Override public List<PricePolicy> policies() { return copyList(policies, PricePolicy.class); }
	@Override public List<PricePolicyItem> policyItems() { return copyList(policyItems, PricePolicyItem.class); }
	@Override public Map<String, MemberLevelSnapshotVO> memberLevels() {
		Map<String, MemberLevelSnapshotVO> copy = new LinkedHashMap<>();
		memberLevels.forEach((key, value) -> copy.put(key, copyLevel(value)));
		return Collections.unmodifiableMap(copy);
	}

	static PromotionContextDTO copyContext(PromotionContextDTO source) {
		if (source == null) { return null; }
		PromotionContextDTO copy = new PromotionContextDTO();
		BeanUtils.copyProperties(source, copy);
		if (source.getSkuItems() != null) { copy.setSkuItems(copyList(source.getSkuItems(), PromotionContextDTO.SkuItem.class)); }
		return copy;
	}

	private static MemberLevelSnapshotVO copyLevel(MemberLevelSnapshotVO source) {
		if (source == null) { return null; }
		MemberLevelSnapshotVO copy = new MemberLevelSnapshotVO();
		BeanUtils.copyProperties(source, copy);
		return copy;
	}

	private static <T> List<T> copyList(List<T> source, Class<T> type) {
		if (source == null) { return List.of(); }
		return source.stream().map(value -> {
			T copy = BeanUtils.instantiateClass(type);
			BeanUtils.copyProperties(value, copy);
			return copy;
		}).toList();
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
		return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
