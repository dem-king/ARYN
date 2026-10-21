package com.aryn.cloud.message.api.constant;

/**
 * 业务通知跳转参数（jumpPayload）的载荷键。
 *
 * <p>jumpPayload 落库到 json 列，必须是 JSON 对象：INTERNAL_ROUTE 用 {@link #ROUTE}
 * 承载端内路由，BIZ_DETAIL 用业务标识（如 bizType/bizId）。生产端与消费端共用此约定。
 *
 * @author aryn
 * @since 2026/10/14
 */
public final class MessageJumpConstants {

	/** 端内路由键，如 /pages/delivery/task-detail?id=xxx */
	public static final String ROUTE = "route";

	private MessageJumpConstants() {
	}

}
