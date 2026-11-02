package com.aryn.cloud.common.core.constant;

/**
 * 会话失效 WebSocket 主动推送契约（三期）。
 *
 * <p>服务端在被顶下线/被踢下线时，通过现有消息推送通道向旧设备的 WebSocket 连接下发
 * {@code SESSION_INVALID} 事件，让前端**秒级**弹窗，不必等下一次 HTTP 请求 401。</p>
 *
 * <p>通道与事件字段是跨模块契约：发布方在 {@code common-security}，投递方在
 * {@code aryn-message-biz}，两端都引用本类常量，避免硬编码漂移。</p>
 */
public interface SessionInvalidPushConstants {

	/** 复用消息推送的 Redis 频道（message-biz 订阅该频道并投递本实例持有的 WS 会话） */
	String CHANNEL = "message:push:events";

	/** 事件类型：会话失效（被顶下线/被踢下线） */
	String EVENT_TYPE_SESSION_INVALID = "SESSION_INVALID";

	/** 接收者类型：管理端/配送端（TOB） */
	String RECIPIENT_TYPE_SYS_USER = "SYS_USER";

	/** 接收者类型：C 端商城（TOC） */
	String RECIPIENT_TYPE_MALL_USER = "MALL_USER";

}
