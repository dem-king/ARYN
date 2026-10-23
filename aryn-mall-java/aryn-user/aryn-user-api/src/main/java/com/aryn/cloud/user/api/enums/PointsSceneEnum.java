package com.aryn.cloud.user.api.enums;

import lombok.Getter;

/**
 * 积分触发场景。
 *
 * <p>发放类场景对应 {@code points_config.rule_type=1}，消耗类对应 {@code rule_type=2}。
 * 仅 {@link #isConfigurable()} 为 true 的场景允许在积分配置中维护，
 * 其余场景由固定业务链路写入，避免运营误配。
 */
@Getter
public enum PointsSceneEnum {

	REGISTER("REGISTER", "注册奖励", "1"),

	ORDER_COMPLETE("ORDER_COMPLETE", "订单完成", "1"),

	SIGN_IN("SIGN_IN", "签到奖励", "1"),

	RECHARGE("RECHARGE", "充值赠送", "1"),

	ORDER_REFUND("ORDER_REFUND", "退款返还", "1"),

	ORDER_DEDUCT("ORDER_DEDUCT", "积分抵现", "2"),

	ADMIN_ADJUST("ADMIN_ADJUST", "后台调整", null);

	private final String code;

	private final String label;

	private final String changeType;

	PointsSceneEnum(String code, String label, String changeType) {
		this.code = code;
		this.label = label;
		this.changeType = changeType;
	}

	public static PointsSceneEnum fromCode(String code) {
		for (PointsSceneEnum scene : values()) {
			if (scene.code.equals(code)) {
				return scene;
			}
		}
		return null;
	}

	public boolean isConfigurable() {
		return this == REGISTER || this == ORDER_COMPLETE || this == ORDER_DEDUCT;
	}

	public boolean isEarnScene() {
		return "1".equals(changeType);
	}

	public boolean isSpendScene() {
		return "2".equals(changeType);
	}

}
