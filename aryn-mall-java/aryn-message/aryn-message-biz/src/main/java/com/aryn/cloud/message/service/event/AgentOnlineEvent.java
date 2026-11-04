package com.aryn.cloud.message.service.event;

/**
 * 客服坐席上线事件。
 *
 * <p>{@code AgentServiceImpl#setPresence} 成功把坐席状态置为 ONLINE 后发布。
 * 独立监听器（{@code listener.AgentOnlineEventListener}）用它触发一轮共享池待领取会话的
 * best-effort 回收，避免 AgentService 直接依赖分配服务造成构造器循环依赖。</p>
 *
 * @param tenantId 租户 ID
 * @param staffId 上线的工作人员 ID
 */
public record AgentOnlineEvent(String tenantId, String staffId) {

}
