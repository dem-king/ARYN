package com.aryn.cloud.common.ip.updater;

/**
 * xdb 下载结果。
 *
 * @param status HTTP 状态码（304 表示未变化）
 * @param etag 响应 ETag，可能为 null
 * @param lastModified 响应 Last-Modified，可能为 null
 * @param bytes 写入本地文件的字节数（304 时为 0）
 */
public record IpRegionDownloadResult(int status, String etag, String lastModified, long bytes) {

	/** HTTP 304 Not Modified */
	public static final int NOT_MODIFIED = 304;

	public boolean notModified() {
		return status == NOT_MODIFIED;
	}

}
