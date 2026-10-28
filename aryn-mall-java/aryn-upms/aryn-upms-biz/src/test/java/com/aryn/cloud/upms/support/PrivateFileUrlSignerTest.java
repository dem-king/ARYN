package com.aryn.cloud.upms.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateFileUrlSignerTest {

	private static final String KEY_HEX = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

	private PrivateFileUrlSigner signer;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(PrivateFileUrlSigner.KEY_REDIS_KEY)).thenReturn(KEY_HEX);
		signer = new PrivateFileUrlSigner(redisTemplate);
	}

	@Test
	void signsAndVerifiesPrivateUrl() {
		String signed = signer.sign("/boot/file/local/tenant-a/private/photo.jpg");
		Map<String, String> params = queryParams(signed);

		assertThat(signed).contains("expires=", "signature=");
		assertThat(signer.verify("private/photo.jpg", "tenant-a",
				Long.valueOf(params.get("expires")), params.get("signature"))).isTrue();
	}

	@Test
	void rejectsTamperedPathOrTenant() {
		String signed = signer.sign("/boot/file/local/tenant-a/private/photo.jpg");
		Map<String, String> params = queryParams(signed);

		assertThat(signer.verify("private/other.jpg", "tenant-a",
				Long.valueOf(params.get("expires")), params.get("signature"))).isFalse();
		assertThat(signer.verify("private/photo.jpg", "tenant-b",
				Long.valueOf(params.get("expires")), params.get("signature"))).isFalse();
	}

	@Test
	void rejectsExpiredOrMissingSignature() {
		String signed = signer.sign("/boot/file/local/tenant-a/private/photo.jpg");
		Map<String, String> params = queryParams(signed);

		assertThat(signer.verify("private/photo.jpg", "tenant-a", 1L, params.get("signature"))).isFalse();
		assertThat(signer.verify("private/photo.jpg", "tenant-a", Long.valueOf(params.get("expires")), null))
			.isFalse();
	}

	@Test
	void leavesPublicUrlUntouched() {
		String url = "/boot/file/local/tenant-a/public.jpg";

		assertThat(signer.sign(url)).isEqualTo(url);
		assertThat(signer.sign("https://cdn.example.com/oss/photo.jpg")).isEqualTo("https://cdn.example.com/oss/photo.jpg");
	}

	private static Map<String, String> queryParams(String url) {
		String query = url.substring(url.indexOf('?') + 1);
		return Arrays.stream(query.split("&"))
			.map(pair -> pair.split("=", 2))
			.collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
	}

}
