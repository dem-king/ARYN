package com.aryn.cloud.common.ip.updater;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 默认 HTTP 下载实现：支持 ETag 条件请求（304 直接跳过 11MB 下载）。
 */
@Slf4j
public class HttpIpRegionDownloader implements IpRegionDownloader {

	private static final String USER_AGENT = "aryn-mall-ip2region-refresh/1.0";

	private final int timeoutMs;

	public HttpIpRegionDownloader(int timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	@Override
	public IpRegionDownloadResult download(String url, Path tempFile, String etag) throws IOException {
		long start = System.currentTimeMillis();
		HttpRequest request = HttpUtil.createGet(url)
			.setFollowRedirects(true)
			.setConnectionTimeout(timeoutMs)
			.setReadTimeout(timeoutMs)
			.header("User-Agent", USER_AGENT);
		if (StrUtil.isNotBlank(etag)) {
			request.header("If-None-Match", etag.trim());
		}
		try (HttpResponse response = request.execute()) {
			int status = response.getStatus();
			if (status == IpRegionDownloadResult.NOT_MODIFIED) {
				log.info("xdb 远端未变化（ETag 命中），耗时 {}ms", System.currentTimeMillis() - start);
				return new IpRegionDownloadResult(status, StrUtil.trimToNull(etag), null, 0);
			}
			if (status != 200) {
				throw new IOException("下载 xdb 失败：HTTP " + status + "，url=" + url);
			}
			byte[] body = response.bodyBytes();
			if (body == null || body.length == 0) {
				throw new IOException("下载 xdb 失败：响应体为空，url=" + url);
			}
			Files.write(tempFile, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
			log.info("xdb 下载完成：{} 字节，耗时 {}ms", body.length, System.currentTimeMillis() - start);
			return new IpRegionDownloadResult(status, StrUtil.trimToNull(response.header("ETag")),
					StrUtil.trimToNull(response.header("Last-Modified")), body.length);
		}
	}

}
