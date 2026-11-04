package com.aryn.cloud.message.service;

/** 通知分发任务执行服务。 */
public interface NoticeDispatchService {

	void dispatch(String tenantId, String taskId);

	/**
	 * 恢复某租户心跳超时的分发任务（PENDING/RUNNING 且 last_heartbeat_time 早于阈值）。
	 *
	 * <p>定时任务恢复入口：按租户过滤查询，逐任务复用 {@link #dispatch(String, String)}；
	 * 任务数受 limit 限制。注意 {@code dispatch} 是异步执行，本方法返回的是「已提交恢复」的任务数。
	 *
	 * @return 提交恢复的任务数
	 */
	int recoverTenant(String tenantId, int limit);

}
