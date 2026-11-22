package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 协议价单条解析的共享口径（2026-11-15 审查修复抽取）。
 *
 * <p>引擎成交判定（{@code PromotionEngineServiceImpl#applyPolicyPrices}）、C 端批量查价
 * （{@code PricePolicyService#policyPrices}）与发布预检（dry-run）必须同源：
 * 固定价直取（空/非正数视为未配置）、折扣率按基价折算（两位小数 DOWN）、
 * 起订量未达不落价。此前引擎与查价服务各写一份，固定价空值防护已经出现分歧。
 *
 * @author aryn
 * @since 2026/11/15
 */
final class PolicyPriceResolver {

	private PolicyPriceResolver() {
	}

	/** 单条协议价解析：固定价直取；折扣率按基价计算（两位小数，DOWN，与限时折扣同口径）；非法配置返回 null。 */
	static BigDecimal resolve(PricePolicyItem item, String priceMode, BigDecimal salesPrice) {
		if (item == null || salesPrice == null) {
			return null;
		}
		if (PricePolicy.MODE_FIXED_PRICE.equals(priceMode)) {
			return item.getPolicyPrice() != null && item.getPolicyPrice().signum() > 0
					? item.getPolicyPrice() : null;
		}
		if (PricePolicy.MODE_RATE.equals(priceMode) && item.getPolicyRate() != null
				&& item.getPolicyRate().signum() > 0 && item.getPolicyRate().compareTo(BigDecimal.ONE) <= 0) {
			return salesPrice.multiply(item.getPolicyRate()).setScale(2, RoundingMode.DOWN);
		}
		return null;
	}

	/** 起订量门槛：min_qty 空/小于 1 视为无门槛；购买件数未达起订量时协议价不生效（B2B 合同起订约束）。 */
	static boolean minQtySatisfied(PricePolicyItem item, int quantity) {
		return item == null || item.getMinQty() == null || item.getMinQty() <= 1 || quantity >= item.getMinQty();
	}

}