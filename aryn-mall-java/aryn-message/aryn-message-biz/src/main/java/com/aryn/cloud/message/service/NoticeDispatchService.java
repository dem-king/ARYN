package com.aryn.cloud.message.service;

/** 通知分发任务执行服务。 */
public interface NoticeDispatchService {

	void dispatch(String tenantId, String taskId);

}
