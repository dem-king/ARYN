package com.aryn.cloud.message.api.enums;

/** 通知分发聚合状态，由同一通知下的多个目标端任务汇总得出。 */
public enum NoticeDispatchStatus {

	NONE,
	RUNNING,
	SUCCEEDED,
	PARTIAL_FAILED,
	FAILED

}
