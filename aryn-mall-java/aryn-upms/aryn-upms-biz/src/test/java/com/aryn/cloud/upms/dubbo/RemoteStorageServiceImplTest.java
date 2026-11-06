package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.storage.handler.ArynUploadFileHandler;
import com.aryn.cloud.common.storage.handler.StorageFactory;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 远程存储服务（消息图片上传复用）单测：mock 存储配置与 StorageFactory，不触真实文件系统。
 */
class RemoteStorageServiceImplTest {

	private ISysStorageConfigService sysStorageConfigService;
	private StorageFactory storageFactory;
	private RemoteStorageServiceImpl service;

	@BeforeEach
	void setUp() {
		sysStorageConfigService = mock(ISysStorageConfigService.class);
		storageFactory = mock(StorageFactory.class);
		service = new RemoteStorageServiceImpl(sysStorageConfigService, storageFactory);
		ArynTenantContextHolder.setTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void emptyContentIsRejected() {
		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadPublicFile(new byte[0], "a.png", "image/png", 0L, "service-chat"));

		assertEquals("上传文件不能为空", exception.getMsg());
		verifyNoInteractions(storageFactory);
	}

	@Test
	void missingStorageConfigIsRejectedWithReadableMessage() {
		when(sysStorageConfigService.getConfig()).thenReturn(null);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadPublicFile(new byte[] { 1, 2 }, "a.png", "image/png", 2L, "service-chat"));

		assertTrue(exception.getMsg().contains("未配置启用的文件存储"));
		verifyNoInteractions(storageFactory);
	}

	@Test
	void uploadUsesConfiguredStorageStrategy() {
		SysStorageConfigDTO config = new SysStorageConfigDTO();
		config.setType("local");
		ArynUploadFileHandler handler = mock(ArynUploadFileHandler.class);
		when(sysStorageConfigService.getConfig()).thenReturn(config);
		when(storageFactory.getStrategy("local")).thenReturn(handler);
		when(handler.uploadFile(eq(config), any(InputStream.class), eq("a.png"), eq("image/png"), eq(4L)))
			.thenReturn("/file/local/tenant-1/uuid.png");

		String url = service.uploadPublicFile(new byte[] { 1, 2, 3, 4 }, "a.png", "image/png", 4L, "service-chat");

		assertEquals("/file/local/tenant-1/uuid.png", url);
		verify(storageFactory).getStrategy("local");
		verify(handler).uploadFile(eq(config), any(InputStream.class), eq("a.png"), eq("image/png"), anyLong());
	}

	@Test
	void storageFailureIsWrappedAsReadableBusinessError() {
		SysStorageConfigDTO config = new SysStorageConfigDTO();
		config.setType("local");
		ArynUploadFileHandler handler = mock(ArynUploadFileHandler.class);
		when(sysStorageConfigService.getConfig()).thenReturn(config);
		when(storageFactory.getStrategy("local")).thenReturn(handler);
		when(handler.uploadFile(any(), any(InputStream.class), any(), any(), anyLong()))
			.thenThrow(new IllegalStateException("disk full"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadPublicFile(new byte[] { 1 }, "a.png", "image/png", 1L, "service-chat"));

		assertEquals("文件上传失败，请稍后重试", exception.getMsg());
		verify(handler, never()).uploadPrivateFile(any(), any(InputStream.class), any(), any(), anyLong());
	}

}
