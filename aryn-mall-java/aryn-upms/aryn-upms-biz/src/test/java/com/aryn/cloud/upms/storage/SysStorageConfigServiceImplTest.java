package com.aryn.cloud.upms.storage;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysStorageConfig;
import com.aryn.cloud.upms.service.impl.SysStorageConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SysStorageConfigServiceImplTest {

	@TempDir
	Path localRoot;

	@Test
	void acceptsAbsoluteLocalRootAndClearsObjectStorageFields() {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig config = baseConfig("local");
		config.setBucket(localRoot.toString());
		config.setAccessKey("unused");
		config.setAccessSecret("unused");
		config.setEndpoint("unused");

		assertThat(service.saveStorageConfig(config)).isTrue();
		assertThat(config.getAccessKey()).isNull();
		assertThat(config.getAccessSecret()).isNull();
		assertThat(config.getEndpoint()).isNull();
		assertThat(config.getStyleAccessEnabled()).isEqualTo(CommonConstants.NO);
	}

	@ParameterizedTest
	@ValueSource(strings = { "aliyun", "tencent", "minio", "oss", "1", "2", "4" })
	void acceptsCanonicalAndLegacyObjectStorageTypes(String type) {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig config = objectConfig(type);

		assertThat(service.saveStorageConfig(config)).isTrue();
		assertThat(config.getType()).isIn("aliyun", "tencent", "minio");
		if ("minio".equals(config.getType())) {
			assertThat(config.getStyleAccessEnabled()).isEqualTo(CommonConstants.YES);
		}
	}

	@Test
	void mapsLegacyPathStyleOssToMinio() {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig config = objectConfig("oss");
		config.setStyleAccessEnabled(CommonConstants.YES);

		assertThat(service.saveStorageConfig(config)).isTrue();
		assertThat(config.getType()).isEqualTo("minio");
	}

	@Test
	void requiresPublicDomainForQiniu() {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig config = objectConfig("qiniu");

		assertThatThrownBy(() -> service.saveStorageConfig(config))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("公开访问域名"));

		config.setDomain("https://cdn.example.com");
		assertThat(service.saveStorageConfig(config)).isTrue();
	}

	@Test
	void rejectsRelativeLocalRootAndUnsafeObjectDirectory() {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig local = baseConfig("local");
		local.setBucket("uploads");

		assertThatThrownBy(() -> service.saveStorageConfig(local))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("绝对路径"));

		SysStorageConfig objectStorage = objectConfig("aliyun");
		objectStorage.setDir("../private");
		assertThatThrownBy(() -> service.saveStorageConfig(objectStorage))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("对象目录"));
	}

	@Test
	void rejectsUnsupportedType() {
		TestStorageConfigService service = new TestStorageConfigService();
		SysStorageConfig config = baseConfig("ftp");
		config.setBucket("uploads");

		assertThatThrownBy(() -> service.saveStorageConfig(config))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).contains("不支持"));
	}

	private SysStorageConfig baseConfig(String type) {
		SysStorageConfig config = new SysStorageConfig();
		config.setType(type);
		config.setStatus(CommonConstants.YES);
		return config;
	}

	private SysStorageConfig objectConfig(String type) {
		SysStorageConfig config = baseConfig(type);
		config.setBucket("mall-assets");
		config.setAccessKey("access-key");
		config.setAccessSecret("access-secret");
		config.setEndpoint("https://storage.example.com");
		return config;
	}

	private static class TestStorageConfigService extends SysStorageConfigServiceImpl {

		@Override
		public boolean save(SysStorageConfig entity) {
			return true;
		}

		@Override
		public void updateStatus() {
		}

	}

}
