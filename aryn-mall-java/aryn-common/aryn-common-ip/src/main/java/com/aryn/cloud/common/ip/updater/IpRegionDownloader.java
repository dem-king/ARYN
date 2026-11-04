package com.aryn.cloud.common.ip.updater;

import java.io.IOException;
import java.nio.file.Path;

/**
 * xdb 数据下载器（默认 HTTP 实现，测试可替换为本地文件/桩实现）。
 */
@FunctionalInterface
public interface IpRegionDownloader {

	/**
	 * 下载远端 xdb 到本地临时文件。
	 * @param url 下载地址
	 * @param tempFile 临时文件（调用方保证父目录已存在）
	 * @param etag 上次下载的 ETag，为 null 表示无条件下载
	 * @return 下载结果；实现可返回 304 表示远端未变化
	 * @throws IOException 网络或写盘失败
	 */
	IpRegionDownloadResult download(String url, Path tempFile, String etag) throws IOException;

}
