package com.aryn.cloud.common.ip.updater;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.aryn.cloud.common.ip.IpRegionFiles;
import com.aryn.cloud.common.ip.config.IpLocationProperties;
import com.aryn.cloud.common.ip.provider.Ip2RegionLocationProvider;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;

/**
 * ip2region xdb 热更新。
 *
 * <p>流程：ETag 条件下载 → 文件校验（体积 / 结构 / 样本 IP 查询）→ 备份旧文件 →
 * 原子替换 → 重建查询服务（{@link Ip2RegionLocationProvider#reload(Path)}）→ 记录元数据。
 * 任一步失败都保持旧数据可用，并把新文件回滚，避免损坏数据常驻。</p>
 *
 * <p>多实例共享同一数据目录时用文件锁串行化：拿不到锁的实例直接跳过，由拿到锁的实例更新
 * （XXL-JOB 分片广播场景无需每个实例都下载 11MB）。</p>
 */
@Slf4j
public class IpRegionDataUpdater {

	private static final String META_ETAG = "etag";

	private static final String META_SHA256 = "sha256";

	private static final String META_DATA_BUILT_AT = "dataBuiltAt";

	private final IpLocationProperties properties;

	private final Ip2RegionLocationProvider provider;

	private final IpRegionDownloader downloader;

	public IpRegionDataUpdater(IpLocationProperties properties, Ip2RegionLocationProvider provider) {
		this(properties, provider, new HttpIpRegionDownloader(properties.getRefresh().getTimeoutMs()));
	}

	public IpRegionDataUpdater(IpLocationProperties properties, Ip2RegionLocationProvider provider,
			IpRegionDownloader downloader) {
		this.properties = properties;
		this.provider = provider;
		this.downloader = downloader;
	}

	/**
	 * 检查并更新 xdb 数据。
	 * @param force true 表示忽略 ETag 强制下载（运维手动刷新用）
	 * @return 更新结果；失败时旧数据保持可用
	 */
	public IpRegionRefreshResult refresh(boolean force) {
		long start = System.currentTimeMillis();
		IpLocationProperties.Refresh refresh = properties.getRefresh();
		if (!properties.isEnabled() || !refresh.isEnabled()) {
			return new IpRegionRefreshResult(IpRegionRefreshResult.Status.DISABLED,
					"热更新未启用（aryn.ip.enabled / aryn.ip.refresh.enabled）", 0, null,
					provider.dataBuiltAtEpochSeconds(), elapsed(start));
		}
		Path target = provider.externalXdbPath();
		Path dataDir = target.getParent();
		Path temp = dataDir.resolve(target.getFileName() + IpRegionFiles.DOWNLOAD_SUFFIX);
		Path backup = dataDir.resolve(target.getFileName() + IpRegionFiles.BACKUP_SUFFIX);
		Path meta = dataDir.resolve(IpRegionFiles.META_FILE_NAME);
		Path lockFile = dataDir.resolve(IpRegionFiles.LOCK_FILE_NAME);
		FileChannel channel = null;
		FileLock lock = null;
		try {
			Files.createDirectories(dataDir);
			channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			try {
				lock = channel.tryLock();
			}
			catch (OverlappingFileLockException e) {
				// 同一 JVM 内并发触发（如手动执行 + 调度撞车）：等价于其他实例正在更新
				lock = null;
			}
			if (lock == null) {
				return new IpRegionRefreshResult(IpRegionRefreshResult.Status.SKIPPED,
						"其他实例正在更新 xdb，本实例跳过", 0, null, provider.dataBuiltAtEpochSeconds(),
						elapsed(start));
			}
			return doRefresh(refresh, target, temp, backup, meta, force, start);
		}
		catch (Exception e) {
			log.warn("ip2region xdb 热更新失败：{}", e.getMessage(), e);
			return new IpRegionRefreshResult(IpRegionRefreshResult.Status.FAILED, "更新失败：" + e.getMessage(), 0,
					null, provider.dataBuiltAtEpochSeconds(), elapsed(start));
		}
		finally {
			deleteQuietly(temp);
			closeQuietly(lock, channel);
		}
	}

	private IpRegionRefreshResult doRefresh(IpLocationProperties.Refresh refresh, Path target, Path temp, Path backup,
			Path meta, boolean force, long start) throws IOException {
		String previousEtag = force ? null : readMetaValue(meta, META_ETAG);
		IpRegionDownloadResult downloaded = downloader.download(refresh.getSourceUrl(), temp, previousEtag);
		if (downloaded.notModified()) {
			return new IpRegionRefreshResult(IpRegionRefreshResult.Status.UP_TO_DATE,
					"远端数据未变化（ETag 命中），当前：" + provider.sourceDescription(), 0, previousEtag,
					provider.dataBuiltAtEpochSeconds(), elapsed(start));
		}
		long bytes = downloaded.bytes() > 0 ? downloaded.bytes() : Files.size(temp);
		validate(temp, refresh);
		String sha256 = DigestUtil.sha256Hex(Files.readAllBytes(temp));
		boolean hadOld = Files.isRegularFile(target);
		if (hadOld) {
			move(target, backup);
		}
		try {
			move(temp, target);
			provider.reload(target);
		}
		catch (Exception e) {
			// 回滚：恢复旧文件（provider 尚未切换，仍是旧数据）
			deleteQuietly(target);
			if (hadOld) {
				try {
					move(backup, target);
				}
				catch (IOException restoreError) {
					log.error("xdb 回滚失败，需人工恢复备份文件：{}", backup, restoreError);
				}
			}
			throw e instanceof IOException ioException ? ioException
					: new IOException("新数据加载失败：" + e.getMessage(), e);
		}
		writeMeta(meta, refresh.getSourceUrl(), downloaded, sha256, provider.dataBuiltAtEpochSeconds());
		String message = StrUtil.format("xdb 已更新：{} 字节，sha256={}，{}", bytes, sha256.substring(0, 12),
				provider.sourceDescription());
		return new IpRegionRefreshResult(IpRegionRefreshResult.Status.UPDATED, message, bytes, downloaded.etag(),
				provider.dataBuiltAtEpochSeconds(), elapsed(start));
	}

	/**
	 * 文件校验：体积下限 + xdb 结构 + 样本 IP 查询，三者都通过才允许替换。
	 */
	private void validate(Path file, IpLocationProperties.Refresh refresh) throws IOException {
		long size = Files.size(file);
		if (size < refresh.getMinBytes()) {
			throw new IOException("xdb 文件体积异常：" + size + " 字节 < 下限 " + refresh.getMinBytes());
		}
		try {
			Searcher.verifyFromFile(file.toFile());
		}
		catch (Exception e) {
			throw new IOException("xdb 结构校验失败：" + e.getMessage(), e);
		}
		List<String> sampleIps = refresh.getSampleIps();
		if (sampleIps == null || sampleIps.isEmpty()) {
			return;
		}
		Searcher searcher = null;
		try {
			searcher = Searcher.newWithFileOnly(Version.IPv4, file.toFile());
			for (String ip : sampleIps) {
				String region = searcher.search(ip);
				if (StrUtil.isBlank(region) || !region.contains("|")) {
					throw new IOException("xdb 样本校验失败：" + ip + " -> " + region);
				}
			}
		}
		catch (IOException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IOException("xdb 样本校验失败：" + e.getMessage(), e);
		}
		finally {
			if (searcher != null) {
				try {
					searcher.close();
				}
				catch (Exception e) {
					log.debug("关闭校验用 searcher 失败：{}", e.getMessage());
				}
			}
		}
	}

	private static void move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String readMetaValue(Path meta, String key) {
		if (!Files.isRegularFile(meta)) {
			return null;
		}
		Properties properties = new Properties();
		try (InputStream inputStream = Files.newInputStream(meta)) {
			properties.load(inputStream);
			return StrUtil.trimToNull(properties.getProperty(key));
		}
		catch (Exception e) {
			log.debug("读取 xdb 元数据失败：{}", e.getMessage());
			return null;
		}
	}

	private void writeMeta(Path meta, String url, IpRegionDownloadResult downloaded, String sha256,
			long dataBuiltAtEpochSeconds) {
		Properties metaProperties = new Properties();
		metaProperties.setProperty("url", StrUtil.nullToEmpty(url));
		metaProperties.setProperty(META_ETAG, StrUtil.nullToEmpty(downloaded.etag()));
		metaProperties.setProperty("lastModified", StrUtil.nullToEmpty(downloaded.lastModified()));
		metaProperties.setProperty(META_SHA256, sha256);
		metaProperties.setProperty(META_DATA_BUILT_AT, String.valueOf(dataBuiltAtEpochSeconds));
		metaProperties.setProperty("updatedAt", String.valueOf(System.currentTimeMillis()));
		try (OutputStream outputStream = Files.newOutputStream(meta, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			metaProperties.store(outputStream, "ip2region xdb metadata, auto-generated");
		}
		catch (IOException e) {
			// 元数据只影响下次是否重复下载，失败不回滚已生效的数据
			log.warn("写入 xdb 元数据失败：{}", e.getMessage());
		}
	}

	private static void closeQuietly(FileLock lock, FileChannel channel) {
		if (lock != null) {
			try {
				lock.release();
			}
			catch (Exception e) {
				log.debug("释放 xdb 文件锁失败：{}", e.getMessage());
			}
		}
		if (channel != null) {
			try {
				channel.close();
			}
			catch (Exception e) {
				log.debug("关闭 xdb 锁文件失败：{}", e.getMessage());
			}
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (Exception e) {
			log.debug("删除临时文件失败 {}：{}", path, e.getMessage());
		}
	}

	private static long elapsed(long start) {
		return System.currentTimeMillis() - start;
	}

}
