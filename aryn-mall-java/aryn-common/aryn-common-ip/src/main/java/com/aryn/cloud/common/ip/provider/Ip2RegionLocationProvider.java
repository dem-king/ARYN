package com.aryn.cloud.common.ip.provider;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.util.HttpIpLocationResolver;
import com.aryn.cloud.common.core.util.IpLocationResolver;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.ip.config.IpLocationProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ip2region 离线归属地解析器。
 *
 * <p>数据来源优先级：外部可写目录（{@code aryn.ip.ip2region.data-dir}）下的 xdb →
 * classpath 内置 xdb。外部文件是热更新目标：XXL-JOB 下载校验后原子替换，再调用
 * {@link #reload(Path)} 重建查询服务（binding 没有 reload API，只能新建实例后切换）。</p>
 *
 * <p>查询失败/未覆盖的网段（IPv6、新分配 IP）按 {@code aryn.ip.fallback-http} 回退第三方
 * HTTP；两者都失败返回「未知」，绝不抛异常影响登录/业务主流程。</p>
 */
@Slf4j
public class Ip2RegionLocationProvider implements IpLocationResolver, AutoCloseable {

	private static final String CLASSPATH_PREFIX = "classpath:";

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
		.withZone(ZoneId.systemDefault());

	private final IpLocationProperties properties;

	private final IpLocationResolver fallbackResolver;

	private final AtomicReference<Ip2Region> service = new AtomicReference<>();

	private final AtomicReference<String> sourceDescription = new AtomicReference<>("未加载");

	private volatile long dataBuiltAtEpochSeconds;

	public Ip2RegionLocationProvider(IpLocationProperties properties) {
		this(properties, properties.isFallbackHttp() ? new HttpIpLocationResolver() : null);
	}

	/**
	 * 指定兜底解析器（测试注入用；生产由 {@code fallback-http} 控制）。
	 * @param properties 配置
	 * @param fallbackResolver 兜底解析器，null 表示不回退
	 */
	Ip2RegionLocationProvider(IpLocationProperties properties, IpLocationResolver fallbackResolver) {
		this.properties = properties;
		this.fallbackResolver = fallbackResolver;
	}

	/**
	 * 启动加载：外部 xdb 优先，失败回退 classpath；两者都失败则只保留第三方 HTTP 兜底。
	 * <p>无论加载是否成功都注册解析器：避免「离线库不可用」时把归属地整体降级成「未知」。</p>
	 */
	@PostConstruct
	public void init() {
		if (!properties.isEnabled() || !properties.getIp2region().isEnabled()) {
			log.info("IP 归属地离线库未启用，沿用第三方 HTTP 解析");
			return;
		}
		Path external = externalXdbPath();
		if (Files.isRegularFile(external)) {
			try {
				loadFromFile(external);
				IpUtils.registerAddressResolver(this);
				log.info("IP 归属地解析器已注册：{}", sourceDescription.get());
				return;
			}
			catch (Exception e) {
				log.warn("外部 xdb 加载失败（{}），回退 classpath 内置数据", e.getMessage());
			}
		}
		try {
			loadFromClasspath();
		}
		catch (Exception e) {
			log.warn("classpath 内置 xdb 加载失败，归属地解析将回退第三方 HTTP：{}", e.getMessage());
		}
		IpUtils.registerAddressResolver(this);
		log.info("IP 归属地解析器已注册：{}", sourceDescription.get());
	}

	@Override
	public String resolve(String ip) {
		if (StrUtil.isBlank(ip)) {
			return IpUtils.UNKNOWN;
		}
		String address = ip.trim();
		if (IpUtils.isPrivateIpAddress(address)) {
			return IpUtils.INTRANET;
		}
		Ip2Region current = service.get();
		if (current != null) {
			try {
				String location = IpRegionTextFormatter.format(current.search(address));
				if (IpRegionTextFormatter.isResolved(location)) {
					return location;
				}
			}
			catch (Exception e) {
				log.debug("ip2region 查询失败 ip={}：{}", address, e.getMessage());
			}
		}
		if (fallbackResolver != null) {
			String fallback = fallbackResolver.resolve(address);
			if (IpRegionTextFormatter.isResolved(fallback)) {
				return fallback;
			}
		}
		return IpUtils.UNKNOWN;
	}

	/**
	 * 用新的 xdb 文件重建查询服务并切换（热更新入口）。
	 * <p>先在本地完成文件校验与服务构建，全部成功后才原子切换；失败时保持旧服务不变。</p>
	 * @param xdbFile 新数据文件
	 * @throws Exception 文件/结构校验或用例查询失败
	 */
	public synchronized void reload(Path xdbFile) throws Exception {
		loadFromFile(xdbFile);
		log.info("IP 归属地数据已热更新：{}", sourceDescription.get());
	}

	/**
	 * 外部可写 xdb 路径（热更新目标）。
	 * @return 绝对路径
	 */
	public Path externalXdbPath() {
		String dataDir = StrUtil.trimToEmpty(properties.getIp2region().getDataDir());
		if (StrUtil.isBlank(dataDir)) {
			dataDir = defaultDataDir();
		}
		return Path.of(dataDir).toAbsolutePath().resolve(properties.getIp2region().getFileName());
	}

	/**
	 * 默认数据目录：{@code ~/.aryn/ip2region}。
	 * @return 目录路径
	 */
	public static String defaultDataDir() {
		return Path.of(System.getProperty("user.home", "."), ".aryn", "ip2region").toString();
	}

	/**
	 * 当前是否加载到可用数据。
	 * @return true 表示离线库可用
	 */
	public boolean isAvailable() {
		return service.get() != null;
	}

	/**
	 * 当前数据来源描述（classpath / 文件路径 + 数据构建时间），用于日志与 Job 输出。
	 * @return 描述文本
	 */
	public String sourceDescription() {
		return sourceDescription.get();
	}

	/**
	 * 数据构建时间（xdb header.createdAt，秒级时间戳）。
	 * @return epoch 秒；未加载时为 0
	 */
	public long dataBuiltAtEpochSeconds() {
		return dataBuiltAtEpochSeconds;
	}

	@Override
	public void close() {
		Ip2Region current = service.getAndSet(null);
		if (current != null) {
			try {
				current.close();
			}
			catch (Exception e) {
				log.warn("关闭 ip2region 查询服务失败：{}", e.getMessage());
			}
		}
		IpUtils.resetAddressResolver();
		log.info("IP 归属地解析器已关闭");
	}

	private void loadFromFile(Path xdbFile) throws Exception {
		Path absolute = xdbFile.toAbsolutePath();
		if (!Files.isRegularFile(absolute)) {
			throw new IOException("xdb 文件不存在: " + absolute);
		}
		Config config = Config.custom()
			.setCachePolicy(Config.cachePolicyFromName(properties.getIp2region().getCachePolicy()))
			.setSearchers(properties.getIp2region().getSearchers())
			.setXdbFile(absolute.toFile())
			.asV4();
		Ip2Region candidate = Ip2Region.create(config, null);
		sanityCheck(candidate);
		swap(candidate, config, absolute.toString());
	}

	private void loadFromClasspath() throws Exception {
		String location = properties.getIp2region().getClasspathLocation();
		String path = StrUtil.removePrefix(StrUtil.trimToEmpty(location), CLASSPATH_PREFIX);
		Resource resource = new ClassPathResource(path);
		if (!resource.exists()) {
			throw new IOException("classpath 内置 xdb 不存在: " + location);
		}
		byte[] bytes;
		try (InputStream inputStream = resource.getInputStream()) {
			bytes = inputStream.readAllBytes();
		}
		// setXdbInputStream 只能配合 BufferCache（binding 约束），11MB 全量进内存，登录/日志低并发场景可接受
		Config config = Config.custom()
			.setCachePolicy(Config.BufferCache)
			.setSearchers(properties.getIp2region().getSearchers())
			.setXdbInputStream(new ByteArrayInputStream(bytes))
			.asV4();
		Ip2Region candidate = Ip2Region.create(config, null);
		sanityCheck(candidate);
		swap(candidate, config, CLASSPATH_PREFIX + path);
	}

	/**
	 * 用例校验：抽样 IP 必须能解析出 region，避免把损坏/不兼容的数据当成可用数据。
	 */
	private void sanityCheck(Ip2Region candidate) throws IOException {
		List<String> sampleIps = properties.getRefresh().getSampleIps();
		if (sampleIps == null || sampleIps.isEmpty()) {
			return;
		}
		String sampleIp = sampleIps.get(0);
		try {
			String region = candidate.search(sampleIp);
			if (StrUtil.isBlank(region) || !region.contains("|")) {
				throw new IOException("xdb 用例校验失败: " + sampleIp + " -> " + region);
			}
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("xdb 用例校验失败: " + sampleIp, e);
		}
	}

	private void swap(Ip2Region candidate, Config config, String source) {
		Ip2Region previous = service.getAndSet(candidate);
		dataBuiltAtEpochSeconds = config.header.createdAt & 0xFFFFFFFFL;
		sourceDescription.set(source + "（数据版本 " + config.header.version + "，构建于 "
				+ DATE_TIME_FORMATTER.format(Instant.ofEpochSecond(dataBuiltAtEpochSeconds)) + "）");
		if (previous != null) {
			try {
				previous.close();
			}
			catch (Exception e) {
				log.warn("关闭旧 ip2region 查询服务失败：{}", e.getMessage());
			}
		}
	}

}
