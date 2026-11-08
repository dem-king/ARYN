package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.upms.api.vo.RemoteImageSizeVO;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 图片像素宽高解析（只读图片头，不整图解码）。
 *
 * <p>商品卡按图片真实比例锁占位高度后，瀑布流卡片在图片加载前后高度不变。
 * 只解析受信任来源：本地上传文件直接读磁盘；其它 URL 仅允许与当前租户
 * 文件存储域名同源，避免商品图地址被利用做 SSRF。结果按 URL 做进程内 LRU
 * 缓存，解析失败的 URL 短期不再重试，避免每次翻页都等待超时。</p>
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageSizeResolver {

	private static final String LOCAL_FILE_SEGMENT = "/file/local/";

	private static final int CACHE_LIMIT = 4096;

	/** 单次列表请求最多等待的解析时间；超时先返回已完成条目，后台继续补缓存 */
	private static final long RESOLVE_TIMEOUT_MILLIS = 1000L;

	private static final int REMOTE_TIMEOUT_MILLIS = 800;

	/** 解析失败短期缓存：坏 URL 在 TTL 内不再重试，避免每次翻页都等超时 */
	private static final long UNRESOLVED_TTL_MILLIS = 10 * 60 * 1000L;

	/** 单次请求最多解析的未缓存图片数；已缓存的永远返回，不受此限制 */
	private static final int MAX_URLS_PER_CALL = 120;

	private final ISysStorageConfigService storageConfigService;

	private final LocalFilePathResolver localFilePathResolver;

	private final Map<String, RemoteImageSizeVO> cache = Collections.synchronizedMap(
			new LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, RemoteImageSizeVO> eldest) {
					return size() > CACHE_LIMIT;
				}
			});

	private final Map<String, Long> unresolvedUntil = Collections.synchronizedMap(
			new LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
					return size() > CACHE_LIMIT;
				}
			});

	private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "image-size-resolver");
		thread.setDaemon(true);
		return thread;
	});

	public Map<String, RemoteImageSizeVO> resolve(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return Collections.emptyMap();
		}
		List<String> targets = urls.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.toList();
		if (targets.isEmpty()) {
			return Collections.emptyMap();
		}

		long now = System.currentTimeMillis();
		List<String> missing = targets.stream()
			.filter(url -> !cache.containsKey(url))
			.filter(url -> {
				Long until = unresolvedUntil.get(url);
				return until == null || until <= now;
			})
			.limit(MAX_URLS_PER_CALL)
			.toList();
		if (!missing.isEmpty()) {
			List<String> localRoots = localRoots();
			SysStorageConfigDTO config = activeConfig();
			List<CompletableFuture<Void>> tasks = missing.stream()
				.map(url -> CompletableFuture.runAsync(() -> resolveOne(url, localRoots, config), executor))
				.toList();
			try {
				CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
					.get(RESOLVE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			}
			catch (Exception exception) {
				// 超时或个别失败：先返回已完成条目；后台任务结束后仍会补齐缓存
				log.debug("图片尺寸解析未在 {}ms 内全部完成，先返回已完成条目", RESOLVE_TIMEOUT_MILLIS);
			}
		}

		Map<String, RemoteImageSizeVO> result = new LinkedHashMap<>();
		for (String url : targets) {
			RemoteImageSizeVO size = cache.get(url);
			if (size != null) {
				result.put(url, size);
			}
		}
		return result;
	}

	private void resolveOne(String url, List<String> localRoots, SysStorageConfigDTO config) {
		try {
			RemoteImageSizeVO size = null;
			LocalImagePath localImage = parseLocalPath(url);
			if (localImage != null) {
				size = readLocal(localImage, localRoots);
			}
			if (size == null) {
				size = readRemote(url, config);
			}
			if (size != null) {
				cache.put(url, size);
				unresolvedUntil.remove(url);
				return;
			}
		}
		catch (Exception exception) {
			log.debug("图片尺寸解析失败 url={}", url, exception);
		}
		unresolvedUntil.put(url, System.currentTimeMillis() + UNRESOLVED_TTL_MILLIS);
	}

	/**
	 * 解析本地上传 URL：/file/local/{tenantId}/{fileName}。
	 * 兼容带服务前缀（/boot、/upms）与绝对域名两种写法。
	 */
	private LocalImagePath parseLocalPath(String url) {
		String path;
		try {
			URI uri = URI.create(url);
			path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : url;
		}
		catch (IllegalArgumentException exception) {
			path = url;
		}
		int index = path.indexOf(LOCAL_FILE_SEGMENT);
		if (index < 0) {
			return null;
		}
		String[] segments = path.substring(index + LOCAL_FILE_SEGMENT.length()).split("/");
		if (segments.length < 2 || !StringUtils.hasText(segments[0]) || !StringUtils.hasText(segments[1])) {
			return null;
		}
		String fileName = segments.length == 2 ? segments[1]
				: String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
		return new LocalImagePath(segments[0], fileName);
	}

	private RemoteImageSizeVO readLocal(LocalImagePath localImage, List<String> localRoots) throws Exception {
		for (String root : localRoots) {
			Path path = localFilePathResolver.resolve(root, localImage.tenantId(), localImage.fileName());
			if (!Files.exists(path)) {
				continue;
			}
			try (InputStream inputStream = Files.newInputStream(path)) {
				return readSize(inputStream);
			}
		}
		return null;
	}

	private RemoteImageSizeVO readRemote(String url, SysStorageConfigDTO config) throws Exception {
		if (config == null || !StringUtils.hasText(config.getDomain())) {
			return null;
		}
		URI target = URI.create(url);
		URI domain = URI.create(config.getDomain());
		if (!isHttp(target) || !isHttp(domain) || !StringUtils.hasText(target.getHost())
				|| !target.getHost().equalsIgnoreCase(domain.getHost())) {
			return null;
		}
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		try {
			connection.setConnectTimeout(REMOTE_TIMEOUT_MILLIS);
			connection.setReadTimeout(REMOTE_TIMEOUT_MILLIS);
			connection.setInstanceFollowRedirects(true);
			connection.setRequestProperty("User-Agent", "aryn-image-size-resolver");
			try (InputStream inputStream = connection.getInputStream()) {
				return readSize(inputStream);
			}
		}
		finally {
			connection.disconnect();
		}
	}

	/** 只读图片头取宽高：ImageIO 读到 SOF/IHDR 即返回，不会整图解码 */
	private RemoteImageSizeVO readSize(InputStream inputStream) throws Exception {
		try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
			if (imageInputStream == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(imageInputStream, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0) {
					return null;
				}
				return new RemoteImageSizeVO(width, height);
			}
			finally {
				reader.dispose();
			}
		}
	}

	private List<String> localRoots() {
		try {
			return storageConfigService.getLocalStorageRoots();
		}
		catch (Exception exception) {
			log.debug("读取本地存储根目录失败，跳过本地图片来源", exception);
			return Collections.emptyList();
		}
	}

	private SysStorageConfigDTO activeConfig() {
		try {
			return storageConfigService.getConfig();
		}
		catch (Exception exception) {
			log.debug("读取文件存储配置失败，跳过远程图片来源", exception);
			return null;
		}
	}

	private boolean isHttp(URI uri) {
		return uri != null
				&& ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
	}

	private record LocalImagePath(String tenantId, String fileName) {
	}

}
