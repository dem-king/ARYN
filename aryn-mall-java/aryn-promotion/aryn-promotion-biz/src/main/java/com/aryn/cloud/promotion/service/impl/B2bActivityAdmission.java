package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import org.springframework.util.StringUtils;

import java.util.Set;

/** 写入准入；历史读取、价格快照与退款补偿不经过该门禁。 */
public final class B2bActivityAdmission {

	public static final Set<String> MANUAL_TYPES = Set.of("4", "5", "7", "12");

	private B2bActivityAdmission() { }

	public static void requireManual(PromotionActivity activity) {
		if (activity == null) {
			throw new ArynBusinessException("活动不能为空");
		}
		if (StringUtils.hasText(activity.getActivityCode()) && activity.getActivityCode().startsWith("SYS_")) {
			throw new ArynBusinessException("系统托管活动由商品专区标签自动维护，不可手工编辑或删除");
		}
		if (PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
			throw new ArynBusinessException("会员价活动已下线：请在商品列表「专区打标」或商品编辑「专区标签」维护");
		}
		if (PromotionActivity.TYPE_CLEARANCE.equals(activity.getActivityType())) {
			throw new ArynBusinessException("出清价仅通过商品专区标签维护");
		}
		if (!MANUAL_TYPES.contains(activity.getActivityType() == null ? "" : activity.getActivityType())) {
			throw new ArynBusinessException("活动类型已停运或不支持手工写入：" + activity.getActivityType());
		}
	}

	public static void rejectRetired(String feature) {
		throw new ArynBusinessException(feature + "已停运，请使用 B2B 活动与定向权益");
	}
}
