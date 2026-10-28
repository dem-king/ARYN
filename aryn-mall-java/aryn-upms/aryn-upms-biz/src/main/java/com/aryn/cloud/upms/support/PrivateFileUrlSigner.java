package com.aryn.cloud.upms.support;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 私有文件 URL 签名。
 *
 * <p>配送凭证等私有文件落在本地存储的 {@code private/} 子目录，下载端点不再允许匿名直取；
 * 读取接口为 URL 追加 {@code expires + signature}，浏览器 {@code <img>} 无需额外请求头即可访问。
 * 签名密钥首次使用时生成并存入共享 Redis，多实例/双模式一致；密钥缺失或 Redis 不可用时
 * 验证失败（fail-closed），不会退化成匿名放行。
 */
@Slf4j
@Component
public class PrivateFileUrlSigner {

	static final String KEY_REDIS_KEY = "aryn:storage:private-url-key";

	private static final Duration URL_TTL = Duration.ofHours(24);

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private static final String LOCAL_FILE_MARKER = "/file/local/";

	private static final String PRIVATE_MARKER = "/private/";

	private final StringRedisTemplate redisTemplate;

	private final AtomicReference<byte[]> keyCache = new AtomicReference<>();

	public PrivateFileUrlSigner(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	/**
	 * 为私有文件 URL 追加签名；公开文件或非本地文件原样返回。
	 */
	public String sign(String url) {
		if (StrUtil.isBlank(url)) {
			return url;
		}
		String path = canonicalPath(url);
		if (path == null || !path.contains(PRIVATE_MARKER)) {
			return url;
		}
		long expires = Instant.now().plus(URL_TTL).getEpochSecond();
		String signature = signature(path, expires, key());
		String base = stripQuery(url);
		return base + (base.contains("?") ? "&" : "?") + "expires=" + expires + "&signature=" + signature;
	}

	/**
	 * 校验私有文件访问签名；公开文件不在此方法职责内。
	 */
	public boolean verify(String relativePath, String tenantId, Long expires, String signature) {
		if (StrUtil.isBlank(relativePath) || StrUtil.isBlank(tenantId) || expires == null
				|| StrUtil.isBlank(signature)) {
			return false;
		}
		if (expires < Instant.now().getEpochSecond()) {
			return false;
		}
		String path = LOCAL_FILE_MARKER + tenantId + "/" + relativePath;
		try {
			byte[] expected = signature(path, expires, key()).getBytes(StandardCharsets.UTF_8);
			byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
			return MessageDigest.isEqual(expected, actual);
		}
		catch (Exception e) {
			log.error("私有文件签名校验失败 tenantId={} path={}", tenantId, relativePath, e);
			return false;
		}
	}

	private String canonicalPath(String url) {
		int index = url.indexOf(LOCAL_FILE_MARKER);
		if (index < 0) {
			return null;
		}
		String path = url.substring(index);
		int queryIndex = path.indexOf('?');
		return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
	}

	private String stripQuery(String url) {
		int queryIndex = url.indexOf('?');
		return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
	}

	private String signature(String path, long expires, byte[] key) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			byte[] digest = mac.doFinal((path + "\n" + expires).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception e) {
			throw new IllegalStateException("私有文件签名失败", e);
		}
	}

	private byte[] key() {
		byte[] cached = keyCache.get();
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			cached = keyCache.get();
			if (cached != null) {
				return cached;
			}
			String key = redisTemplate.opsForValue().get(KEY_REDIS_KEY);
			if (StrUtil.isBlank(key)) {
				String candidate = HexFormat.of().formatHex(randomKey());
				Boolean created = redisTemplate.opsForValue().setIfAbsent(KEY_REDIS_KEY, candidate);
				key = Boolean.TRUE.equals(created) ? candidate : redisTemplate.opsForValue().get(KEY_REDIS_KEY);
			}
			if (StrUtil.isBlank(key)) {
				throw new IllegalStateException("无法初始化私有文件签名密钥");
			}
			byte[] bytes = HexFormat.of().parseHex(key);
			keyCache.set(bytes);
			return bytes;
		}
	}

	private byte[] randomKey() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return bytes;
	}

}
