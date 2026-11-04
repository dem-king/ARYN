package com.aryn.cloud.message.service;

import java.util.Map;

/**
 * 客服会话维护服务（XxlJob 每分钟按租户调用）。
 *
 * <p>维护三件事：关闭 24 小时无新消息的 CUSTOMER_SERVICE 会话、回收共享池待领取会话、
 * 按会话真实在办数校正 {@code message_agent.current_active_count} 快照。
 * 与旧的「全租户 + @InterceptorIgnore」扫描不同，本服务始终显式携带 tenantId 查询，
 * 不绕过租户拦截器，避免误操作其它租户数据。</p>
 */
public interface ConversationMaintenanceService {

	/**
	 * 执行单个租户的会话维护。
	 *
	 * <p>内部会自行设置/恢复租户上下文（XxlJob 线程没有 HTTP 过滤器写入的上下文）。
	 * 单项维护失败只记日志，不中断其它项；返回统计信息，异常情况统计值可能为 0。</p>
	 *
	 * @param tenantId 租户 ID
	 * @return {@code {closed, reassigned, repaired}} 三类处理数量
	 */
	Map<String, Object> maintainTenant(String tenantId);

}
