package com.aryn.cloud.product.service.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专区标签规则校验（批量与单商品共用）。
 *
 * <p>统一口径：出清标签需要「商品出清价/折扣率」或「专区默认折扣率」至少一项，否则无效；
 * 会员标签允许不配商品规则（按专区默认折扣/等级折扣兜底），但会产出告警。
 *
 * @author aryn
 * @since 2026/11/04
 */
public final class GoodsZoneTagRuleValidator {

	private GoodsZoneTagRuleValidator() {
	}

	/**
	 * 校验批量预检请求：预检允许「尚未选择动作」（全部 NONE），用于展示当前专区状态；
	 * 只校验商品列表与已填写规则，不要求至少一个动作。
	 */
	public static void validatePreviewPayload(GoodsZoneTagBatchDTO dto) {
		if (dto == null || CollectionUtils.isEmpty(dto.getSpuIds())) {
			throw new ArynBusinessException("请选择商品");
		}
		validateRate(dto.getMemberDiscountRate(), "会员折扣率");
		validateRate(dto.getClearanceDiscountRate(), "出清折扣率");
		validatePositive(dto.getMemberPriceFixed(), "会员固定价");
		validatePositive(dto.getClearancePrice(), "出清价");
		validateExpireTime(dto.getClearanceExpireTime());
	}

	/** 校验批量执行请求级参数（任一非法 → 整单拒绝；执行必须至少有一个动作）。 */
	public static void validateBatchPayload(GoodsZoneTagBatchDTO dto) {
		if (dto == null || CollectionUtils.isEmpty(dto.getSpuIds())) {
			throw new ArynBusinessException("请选择商品");
		}
		if (dto.getMemberTag() == null && dto.getClearanceTag() == null) {
			throw new ArynBusinessException("请选择会员或出清标签操作");
		}
		validateRate(dto.getMemberDiscountRate(), "会员折扣率");
		validateRate(dto.getClearanceDiscountRate(), "出清折扣率");
		validatePositive(dto.getMemberPriceFixed(), "会员固定价");
		validatePositive(dto.getClearancePrice(), "出清价");
		validateExpireTime(dto.getClearanceExpireTime());
	}

	/** 校验单商品保存请求级参数。 */
	public static void validateSavePayload(GoodsZoneTagSaveDTO dto) {
		if (dto == null) {
			throw new ArynBusinessException("标签内容不能为空");
		}
		if (dto.getMemberTag() == null && dto.getClearanceTag() == null
				&& !Boolean.TRUE.equals(dto.getClearMemberRule())
				&& !Boolean.TRUE.equals(dto.getClearClearanceRule())
				&& CollectionUtils.isEmpty(dto.getSkus())) {
			throw new ArynBusinessException("标签内容不能为空");
		}
		validateRate(dto.getMemberDiscountRate(), "会员折扣率");
		validateRate(dto.getClearanceDiscountRate(), "出清折扣率");
		validatePositive(dto.getMemberPriceFixed(), "会员固定价");
		validatePositive(dto.getMemberPriceFloor(), "会员价下限");
		validatePositive(dto.getClearancePrice(), "出清价");
		validateExpireTime(dto.getClearanceExpireTime());
		if (dto.getSkus() != null) {
			for (GoodsZoneTagSaveDTO.SkuTag item : dto.getSkus()) {
				if (item == null) {
					continue;
				}
				validateRate(item.getMemberDiscountRate(), "SKU 会员折扣率");
				validateRate(item.getClearanceDiscountRate(), "SKU 出清折扣率");
				validatePositive(item.getMemberPriceFixed(), "SKU 会员固定价");
				validatePositive(item.getMemberPriceFloor(), "SKU 会员价下限");
				validatePositive(item.getClearancePrice(), "SKU 出清价");
				validateExpireTime(item.getClearanceExpireTime());
			}
		}
	}

	/**
	 * 出清标签可用性：必须存在商品出清价/折扣率，或专区默认折扣率。
	 */
	public static boolean clearanceRuleAvailable(BigDecimal clearancePrice, BigDecimal clearanceRate,
			BigDecimal zoneDefaultRate) {
		return clearancePrice != null
				|| (clearanceRate != null && clearanceRate.signum() > 0)
				|| (zoneDefaultRate != null && zoneDefaultRate.signum() > 0);
	}

	/**
	 * 出清标签生效校验：若按折扣率算价要求商品有原价。
	 *
	 * @param clearanceTag 是否打/保持出清标签
	 * @param clearancePrice 出清固定价（本次提交值，可空）
	 * @param clearanceRate 出清折扣率（本次提交值，可空）
	 * @param storedPrice 已存出清固定价（可空）
	 * @param storedRate 已存出清折扣率（可空）
	 * @param zoneDefaultRate 专区默认折扣率（可空）
	 * @param originalPrice 商品原价（可空）
	 */
	public static void validateClearanceAvailability(Boolean clearanceTag, BigDecimal clearancePrice,
			BigDecimal clearanceRate, BigDecimal storedPrice, BigDecimal storedRate, BigDecimal zoneDefaultRate,
			BigDecimal originalPrice) {
		if (!Boolean.TRUE.equals(clearanceTag)) {
			return;
		}
		BigDecimal effectivePrice = clearancePrice != null ? clearancePrice : storedPrice;
		BigDecimal effectiveRate = clearanceRate != null ? clearanceRate : storedRate;
		if (!clearanceRuleAvailable(effectivePrice, effectiveRate, zoneDefaultRate)) {
			throw new ArynBusinessException("出清商品必须配置出清价或出清折扣（专区默认折扣未配置）");
		}
		if (effectivePrice == null && effectiveRate != null
				&& (originalPrice == null || originalPrice.signum() <= 0)) {
			throw new ArynBusinessException("商品缺少原价，无法按出清折扣算价，请配置出清固定价");
		}
	}

	public static void validateRate(BigDecimal rate, String label) {
		if (rate != null && (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0)) {
			throw new ArynBusinessException(label + "必须在 (0, 1) 区间内，如 0.9 表示 9 折");
		}
	}

	public static void validatePositive(BigDecimal value, String label) {
		if (value != null && value.signum() <= 0) {
			throw new ArynBusinessException(label + "必须大于 0");
		}
	}

	public static void validateExpireTime(LocalDateTime expireTime) {
		if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException("出清截止时间不能早于当前时间");
		}
	}

}
