package com.aryn.cloud.upms.storage;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.storage.entity.StoredObject;
import com.aryn.cloud.common.storage.handler.ArynUploadFileHandler;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageFactoryCompatibilityTest {

	@Test
	void legacyUploadHandlerCanUseNewObjectResultWithoutModification() {
		ArynUploadFileHandler legacyHandler = new ArynUploadFileHandler() {
			@Override
			public String uploadFile(SysStorageConfigDTO config, InputStream inputStream, String fileName,
					String contextType, long size) {
				return "https://files.example.com/legacy.png";
			}

			@Override
			public String getType() {
				return "legacy";
			}
		};

		StoredObject storedObject = legacyHandler.uploadObject(new SysStorageConfigDTO(), InputStream.nullInputStream(),
			"legacy.png", "image/png", 0);

		assertThat(storedObject.getObjectKey()).isNull();
		assertThat(storedObject.getUrl()).isEqualTo("https://files.example.com/legacy.png");
	}

	@Test
	void canonicalAndLegacyCloudStorageTypesUseS3CompatibleHandler() {
		ArynUploadFileHandler ossHandler = mock(ArynUploadFileHandler.class);
		when(ossHandler.getType()).thenReturn("oss");

		StorageFactory storageFactory = new StorageFactory(List.of(ossHandler));

		assertThat(storageFactory.getStrategy("oss")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("aliyun")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("qiniu")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("tencent")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("minio")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("1")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("2")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("3")).isSameAs(ossHandler);
		assertThat(storageFactory.getStrategy("4")).isSameAs(ossHandler);
		assertThatThrownBy(() -> storageFactory.getStrategy("unknown"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unknown");
	}

}
