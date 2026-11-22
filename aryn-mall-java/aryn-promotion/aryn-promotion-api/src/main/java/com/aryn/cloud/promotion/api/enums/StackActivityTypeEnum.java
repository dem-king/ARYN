package com.aryn.cloud.promotion.api.enums;

/**
 * 参与叠加策略判定的活动类型统一编码。
 *
 * <p>订单侧（券/积分 eligible 判定）与营销侧（远程上下文、引擎覆盖）共用该编码，
 * 避免各自硬编码字符串导致两端口径漂移。默认值口径见营销优惠叠加规则设计说明书 §1.2：
 * 拼团/限时折扣默认可叠（兼容现网行为），秒杀强制不可叠（有赞官方互斥 + 秒杀规则页已对外承诺），
 * 新人专享默认不可叠（防 0 元单/薅羊毛，悦航购自定策略），会员价默认可叠（身份常态权益，非止血促销）。
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
	NEWBIE("newbie", false, false),

	/**
	 * 会员价（promotion_activity type=10）。
	 *
	 * <p>会员价是身份常态权益（非止血促销），默认可叠券/积分，管理端可显式关闭。
	 */
	MEMBER_PRICE("memberPrice", true, true),

	/**
	 * 出清价（promotion_activity type=11）。
	 *
	 * <p>与会员价同为 stackPriority=2，同 SKU 由引擎取低；默认可叠券/积分，
	 * 出清命中时新人价与限时折扣被阻断。
	 */
	CLEARANCE("clearance", true, true),

	/**
	 * 档期特供价（promotion_activity type=12，2026-11-14 B2B 重构新增）。
	 *
	 * <p>档期内限量特惠，活动级限量熔断 + 每人限购；与秒杀同语义（硬促销不叠券/积分），
	 * 但无整点场次，按采购档期窗组织。
	 */
	CALENDAR_SPECIAL("calendarSpecial", false, false),

	/**
	 * 阶梯价（promotion_activity type=4，2026-11-15 审查修复补编码）。
	 *
	 * <p>按数量分档单价（stackPriority=7）；与会员/企业折扣率取优不叠加，
	 * 默认可叠券/积分（订单级让利可叠在基价之上，方案 §4.1）。
	 */
	LADDER("ladder", true, true),

	/**
	 * 协议价（price_policy 承载，锁/快照编码 8，2026-11-14 B2B 重构新增）。
	 *
	 * <p>成交基价由引擎锁定，任何公开活动价不可覆盖；但订单级让利（满量优惠/定向券/积分）
	 * 按方案 §4.1「订单级让利可叠在基价之上」默认可叠，不再强制独占
	 * （2026-11-15 审查修复：原实现禁券禁积分为规格未要求的额外限制）。
	 */
	POLICY("policy", true, true);

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
