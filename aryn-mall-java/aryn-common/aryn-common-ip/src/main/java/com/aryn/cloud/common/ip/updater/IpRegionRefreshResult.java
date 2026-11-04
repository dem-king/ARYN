package com.aryn.cloud.common.ip.updater;

/**
 * xdb 热更新结果。
 *
 * @param status 处理状态
 * @param message 面向 Job 日志/运维的说明
 * @param bytes 本次更新下载的字节数（未更新时为 0）
 * @param etag 本次数据 ETag，可能为 null
 * @param dataBuiltAtEpochSeconds 当前生效数据的构建时间（epoch 秒），未加载时为 0
 * @param costMs 耗时（毫秒）
 */
public record IpRegionRefreshResult(Status status, String message, long bytes, String etag,
		long dataBuiltAtEpochSeconds, long costMs) {

	public enum Status {

		/** 热更新未启用 */
		DISABLED,
		/** 远端未变化（ETag 命中） */
		UP_TO_DATE,
		/** 已更新并生效 */
		UPDATED,
		/** 其他实例正在更新，本实例跳过 */
		SKIPPED,
		/** 更新失败（旧数据保持不变） */
		FAILED

	}

	public boolean failed() {
		return status == Status.FAILED;
	}

}
