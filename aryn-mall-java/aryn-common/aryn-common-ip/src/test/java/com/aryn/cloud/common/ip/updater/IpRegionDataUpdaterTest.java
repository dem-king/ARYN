package com.aryn.cloud.common.ip.updater;

import com.aryn.cloud.common.ip.IpRegionFiles;
import com.aryn.cloud.common.ip.config.IpLocationProperties;
import com.aryn.cloud.common.ip.provider.Ip2RegionLocationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * xdb 热更新：原子替换、ETag 命中跳过、校验失败保留旧数据、下载异常降级、文件锁跳过。
 */
class IpRegionDataUpdaterTest {

	@TempDir
	Path tempDir;

	private Ip2RegionLocationProvider provider;

	@AfterEach
	void tearDown() {
		if (provider != null) {
			provider.close();
			provider = null;
		}
	}

	@Test
	void shouldUpdateAtomicallyAndReloadProvider() throws Exception {
		prepareProvider();
		byte[] xdb = bundledXdb();
		AtomicReference<String> requestedEtag = new AtomicReference<>();
		IpRegionDataUpdater updater = updater((url, tempFile, etag) -> {
			requestedEtag.set(etag);
			Files.write(tempFile, xdb, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return new IpRegionDownloadResult(200, "\"etag-1\"", null, xdb.length);
		});

		IpRegionRefreshResult result = updater.refresh(false);

		assertThat(result.status()).isEqualTo(IpRegionRefreshResult.Status.UPDATED);
		assertThat(result.bytes()).isEqualTo(xdb.length);
		assertThat(requestedEtag.get()).as("首次更新无 ETag").isNull();
		Path target = provider.externalXdbPath();
		assertThat(Files.size(target)).isEqualTo(xdb.length);
		assertThat(provider.sourceDescription()).contains(target.toAbsolutePath().toString());
		assertThat(Files.isRegularFile(tempDir.resolve(IpRegionFiles.META_FILE_NAME))).isTrue();
		assertThat(Searcher.newWithFileOnly(Version.IPv4, target.toFile()).search("223.5.5.5")).contains("|");
		assertThat(provider.resolve("223.5.5.5")).contains("浙江");
	}

	@Test
	void shouldSkipDownloadWhenEtagMatches() throws Exception {
		prepareProvider();
		byte[] xdb = bundledXdb();
		AtomicInteger downloads = new AtomicInteger();
		IpRegionDataUpdater updater = updater((url, tempFile, etag) -> {
			if ("\"etag-1\"".equals(etag)) {
				return new IpRegionDownloadResult(304, etag, null, 0);
			}
			downloads.incrementAndGet();
			Files.write(tempFile, xdb, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return new IpRegionDownloadResult(200, "\"etag-1\"", null, xdb.length);
		});

		assertThat(updater.refresh(false).status()).isEqualTo(IpRegionRefreshResult.Status.UPDATED);
		IpRegionRefreshResult second = updater.refresh(false);

		assertThat(second.status()).isEqualTo(IpRegionRefreshResult.Status.UP_TO_DATE);
		assertThat(downloads.get()).as("第二次不应重复下载").isEqualTo(1);
	}

	@Test
	void shouldForceDownloadIgnoringEtag() throws Exception {
		prepareProvider();
		byte[] xdb = bundledXdb();
		AtomicReference<String> requestedEtag = new AtomicReference<>("unset");
		IpRegionDataUpdater updater = updater((url, tempFile, etag) -> {
			requestedEtag.set(etag);
			Files.write(tempFile, xdb, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return new IpRegionDownloadResult(200, "\"etag-1\"", null, xdb.length);
		});
		updater.refresh(false);
		requestedEtag.set("unset");

		IpRegionRefreshResult result = updater.refresh(true);

		assertThat(result.status()).isEqualTo(IpRegionRefreshResult.Status.UPDATED);
		assertThat(requestedEtag.get()).as("force 时忽略 ETag").isNull();
	}

	@Test
	void shouldKeepOldDataWhenDownloadedFileInvalid() throws Exception {
		prepareProvider();
		byte[] xdb = bundledXdb();
		IpRegionDataUpdater seedUpdater = updater((url, tempFile, etag) -> {
			Files.write(tempFile, xdb, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return new IpRegionDownloadResult(200, "\"etag-1\"", null, xdb.length);
		});
		assertThat(seedUpdater.refresh(false).status()).isEqualTo(IpRegionRefreshResult.Status.UPDATED);
		Path target = provider.externalXdbPath();
		byte[] before = Files.readAllBytes(target);

		byte[] corrupt = new byte[4096];
		Arrays.fill(corrupt, (byte) 1);
		IpRegionDataUpdater brokenUpdater = updater((url, tempFile, etag) -> {
			Files.write(tempFile, corrupt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return new IpRegionDownloadResult(200, "\"etag-2\"", null, corrupt.length);
		});
		IpRegionRefreshResult result = brokenUpdater.refresh(true);

		assertThat(result.status()).isEqualTo(IpRegionRefreshResult.Status.FAILED);
		assertThat(Files.readAllBytes(target)).as("校验失败必须保留旧文件").isEqualTo(before);
		assertThat(provider.resolve("223.5.5.5")).contains("浙江");
	}

	@Test
	void shouldKeepProviderAliveWhenDownloadFails() throws Exception {
		prepareProvider();
		IpRegionDataUpdater updater = updater((url, tempFile, etag) -> {
			throw new IOException("network down");
		});

		IpRegionRefreshResult result = updater.refresh(true);

		assertThat(result.status()).isEqualTo(IpRegionRefreshResult.Status.FAILED);
		assertThat(result.message()).contains("network down");
		assertThat(provider.isAvailable()).isTrue();
		assertThat(provider.resolve("223.5.5.5")).contains("浙江");
	}

	@Test
	void shouldSkipWhenAnotherInstanceHoldsLock() throws Exception {
		prepareProvider();
		Path lockFile = tempDir.resolve(IpRegionFiles.LOCK_FILE_NAME);
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE);
				FileLock ignored = channel.lock()) {
			IpRegionRefreshResult result = updater((url, tempFile, etag) -> {
				throw new AssertionError("不应发起下载");
			}).refresh(false);
			assertThat(result.status()).isEqualTo(IpRegionRefreshResult.Status.SKIPPED);
		}
	}

	@Test
	void shouldReturnDisabledWhenRefreshSwitchOff() {
		IpLocationProperties properties = properties();
		properties.getRefresh().setEnabled(false);
		provider = new Ip2RegionLocationProvider(properties);
		provider.init();
		IpRegionDataUpdater updater = new IpRegionDataUpdater(properties, provider);

		assertThat(updater.refresh(false).status()).isEqualTo(IpRegionRefreshResult.Status.DISABLED);
	}

	private void prepareProvider() {
		IpLocationProperties properties = properties();
		provider = new Ip2RegionLocationProvider(properties);
		provider.init();
		assertThat(provider.isAvailable()).isTrue();
	}

	private IpLocationProperties properties() {
		IpLocationProperties properties = new IpLocationProperties();
		properties.setFallbackHttp(false);
		properties.getIp2region().setDataDir(tempDir.toString());
		properties.getRefresh().setMinBytes(1024);
		properties.getRefresh().setSampleIps(java.util.List.of("223.5.5.5"));
		return properties;
	}

	private IpRegionDataUpdater updater(IpRegionDownloader downloader) {
		return new IpRegionDataUpdater(properties(), provider, downloader);
	}

	private static byte[] bundledXdb() throws IOException {
		try (InputStream inputStream = new ClassPathResource("ip2region/ip2region_v4.xdb").getInputStream()) {
			return inputStream.readAllBytes();
		}
	}

}
