package com.aryn.cloud.common.ip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 归属地配置（{@code aryn.ip.*}）。
 *
 * <p>默认值即可用：classpath 内置 ip2region v4 xdb（11MB）+ 查询失败回退第三方 HTTP。
 * 热更新目标目录默认在 {@code ~/.aryn/ip2region}，可通过 {@code aryn.ip.ip2region.data-dir}
 * 指到共享卷（多副本/多服务共用同一份数据）。</p>
 */
@Data
@ConfigurationProperties(prefix = "aryn.ip")
public class IpLocationProperties {

	/** 总开关：关闭后不注册离线解析器，沿用第三方 HTTP 实现。 */
	private boolean enabled = true;

	/** 离线库查不到时是否回退第三方 HTTP（IPv6、未知新网段等场景的兜底）。 */
	private boolean fallbackHttp = true;

	private Ip2Region ip2region = new Ip2Region();

	private Refresh refresh = new Refresh();

	@Data
	public static class Ip2Region {

		/** 离线库开关。 */
		private boolean enabled = true;

		/**
		 * 外部可写数据目录（热更新的落盘位置），为空时默认 {@code ~/.aryn/ip2region}；
		 * 启动时若该目录下已存在 xdb 则优先使用，否则使用 classpath 内置数据。
		 */
		private String dataDir = "";

		/** 数据文件名（同时用于 classpath 内置资源名）。 */
		private String fileName = "ip2region_v4.xdb";

		/** classpath 内置 xdb 位置。 */
		private String classpathLocation = "classpath:ip2region/ip2region_v4.xdb";

		/** 缓存策略：vindex（512KB 索引 + 随机读，默认）/ buffer（全量进内存）/ nocache（纯文件）。 */
		private String cachePolicy = "vindex";

		/** 查询器数量（binding 3.x 的并发查询池大小）。 */
		private int searchers = 20;

	}

	@Data
	public static class Refresh {

		/** 是否允许热更新（同时决定 XXL-JOB handler 是否注册）。 */
		private boolean enabled = true;

		/**
		 * xdb 下载地址。默认跟随 ip2region master（官方数据不定期更新）；
		 * 境内建议改 Gitee 镜像，商用建议改为已购买的商用离线数据（xdb 或 TXT 生成）。
		 */
		private String sourceUrl = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb";

		/** 下载超时（毫秒）。 */
		private int timeoutMs = 60_000;

		/** 文件体积下限（字节），默认 5MB，用于拦截下载到错误页/截断文件。 */
		private long minBytes = 5L * 1024 * 1024;

		/** 更新后抽样校验的 IP 列表（每个都必须能解析出 region，否则判定文件无效）。 */
		private List<String> sampleIps = new ArrayList<>(List.of("223.5.5.5", "114.114.114.114"));

	}

}
