package com.aryn.cloud.promotion.api.enums;

/**
 * 参与叠加策略判定的活动类型统一编码。
 *
 * <p>订单侧（券/积分 eligible 判定）与营销侧（远程上下文、引擎覆盖）共用该编码，
 * 避免各自硬编码字符串导致两端口径漂移。默认值口径见营销优惠叠加规则设计说明书 §1.2：
 * 拼团/限时折扣默认可叠（兼容现网行为），秒杀强制不可叠（有赞官方互斥 + 秒杀规则页已对外承诺），
 * 新人专享默认不可叠（防 0 元单/薅羊毛，悦航购自定策略）。
 *
 * @author aryn
 * @since 2026/10/24
 */
public enum StackActivityTypeEnum {

	/** 多人拼团（group_buy_activity） */
	GROUP_BUY("groupBuy", true, true),

	/** 秒杀（seckill_activity）：服务端强制不可叠，配置不生效 */
	SECKILL("seckill", false, false),

	/** 限时折扣（discount_activity） */
	DISCOUNT("discount", true, true),

	/** 新人专享价（promotion_activity type=9） */
	NEWBIE("newbie", false, false);

	/** 活动类型编码（远程 VO / 结算响应下发用） */
	private final String code;

	/** 券叠加默认值（活动字段为空时回落） */
	private final boolean defaultCouponStackable;

	/** 积分叠加默认值（活动字段为空时回落） */
	private final boolean defaultPointsStackable;

	StackActivityTypeEnum(String code, boolean defaultCouponStackable, boolean defaultPointsStackable) {
		this.code = code;
		this.defaultCouponStackable = defaultCouponStackable;
		this.defaultPointsStackable = defaultPointsStackable;
	}

	public String getCode() {
		return code;
	}

	public boolean isDefaultCouponStackable() {
		return defaultCouponStackable;
	}

	public boolean isDefaultPointsStackable() {
		return defaultPointsStackable;
	}

	/** 活动表 '1'/'0' 字段 → boolean；空值按类型默认值回落。 */
	public static boolean resolve(String rawValue, boolean defaultValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return defaultValue;
		}
		return !"0".equals(rawValue.trim());
	}

}
