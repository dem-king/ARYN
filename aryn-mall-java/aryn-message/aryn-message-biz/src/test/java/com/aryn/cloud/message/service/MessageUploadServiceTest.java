package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.vo.conversation.MessageUploadVO;
import com.aryn.cloud.message.service.impl.MessageUploadServiceImpl;
import com.aryn.cloud.upms.api.remote.RemoteStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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
 * P2-A 批次二：消息专用图片上传校验测试（mock 远程存储，不依赖真实 upms/文件系统）。
 *
 * <p>锁住口径：扩展名 + 声明 Content-Type + 魔数三重校验；≤5MB；GIF 本期不支持（与发送白名单一致）；
 * 返回 fileId=url 且 URL 必须含 /{tenantId}/（能被发送侧 validateTenantFileUrl 接受）；
 * upms 业务错误透传、Dubbo 包装异常解包、其它异常给可读降级文案。</p>
 */
class MessageUploadServiceTest {

	private static final String TENANT = "tenant-1";
	private static final String STORAGE_URL = "/upms/file/local/tenant-1/9f0f1c6e2a4d4f1e.png";

	private RemoteStorageService remoteStorageService;
	private MessageUploadServiceImpl service;

	@BeforeEach
	void setUp() {
		remoteStorageService = mock(RemoteStorageService.class);
		service = new MessageUploadServiceImpl(remoteStorageService);
	}

	@Test
	void pngUploadReturnsTenantScopedUrlAndFileIdEqualsUrl() {
		when(remoteStorageService.uploadPublicFile(any(), eq("a.png"), eq("image/png"), anyLong(), eq("service-chat")))
			.thenReturn(STORAGE_URL);

		MessageUploadVO result = service.uploadImage(TENANT, file("a.png", "image/png", png()));

		assertEquals(STORAGE_URL, result.getFileId());
		assertEquals(STORAGE_URL, result.getUrl());
		assertEquals("a.png", result.getName());
		assertEquals("image/png", result.getContentType());
		assertEquals((long) png().length, result.getSize());
		// 上传返回的 URL 必须含租户路径，发送侧 validateTenantFileUrl 才允许（联合 ChatMessageQuoteRecallTest 覆盖）。
		assertTrue(result.getUrl().contains("/" + TENANT + "/"));
	}

	@Test
	void jpegAndWebpAreAcceptedAndOctetStreamFallsBackToExtension() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any())).thenReturn(STORAGE_URL);

		MessageUploadVO jpeg = service.uploadImage(TENANT, file("b.jpeg", "image/jpeg", jpeg()));
		MessageUploadVO webp = service.uploadImage(TENANT,
				file("c.webp", "application/octet-stream", webp()));

		assertEquals("image/jpeg", jpeg.getContentType());
		assertEquals("image/webp", webp.getContentType());
		verify(remoteStorageService).uploadPublicFile(any(), eq("b.jpeg"), eq("image/jpeg"), anyLong(),
				eq("service-chat"));
	}

	@Test
	void gifIsRejectedAndRemoteIsNeverCalled() {
		MultipartFile gif = file("d.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, gif));

		// GIF 本期不支持：上传/发送白名单统一 jpeg/png/webp。
		assertEquals("仅支持 JPG/JPEG/PNG/WEBP 图片", exception.getMsg());
		verifyNoInteractions(remoteStorageService);
	}

	@Test
	void emptyAndOversizedFilesAreRejectedBeforeAnyRead() {
		MultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
		ArynBusinessException emptyError = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, empty));
		assertEquals("上传文件不能为空", emptyError.getMsg());

		byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
		MultipartFile oversized = file("big.png", "image/png", tooBig);
		ArynBusinessException sizeError = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, oversized));
		assertEquals("图片大小不能超过 5MB", sizeError.getMsg());

		verifyNoInteractions(remoteStorageService);
	}

	@Test
	void forgedMagicAndDeclaredContentTypeAreRejected() {
		MultipartFile forged = file("fake.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));
		ArynBusinessException magicError = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, forged));
		assertEquals("图片格式不合法", magicError.getMsg());

		MultipartFile wrongContentType = file("a.png", "text/plain", png());
		ArynBusinessException contentTypeError = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, wrongContentType));

		assertEquals("仅支持 JPG/JPEG/PNG/WEBP 图片", contentTypeError.getMsg());
		verifyNoInteractions(remoteStorageService);
	}

	@Test
	void upmsBusinessErrorIsPropagatedToCaller() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any()))
			.thenThrow(new ArynBusinessException("当前租户未配置启用的文件存储，请联系管理员在系统设置-文件存储配置中启用"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, file("a.png", "image/png", png())));

		assertTrue(exception.getMsg().startsWith("当前租户未配置启用的文件存储"));
	}

	@Test
	void dubboWrappedBusinessErrorIsUnwrapped() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any()))
			.thenThrow(new RuntimeException("RpcException",
					new ArynBusinessException("当前租户未配置启用的文件存储，请联系管理员启用")));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, file("a.png", "image/png", png())));

		assertTrue(exception.getMsg().startsWith("当前租户未配置启用的文件存储"));
	}

	@Test
	void unexpectedRemoteFailureUsesReadableMessage() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any()))
			.thenThrow(new IllegalStateException("network down"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, file("a.png", "image/png", png())));

		assertEquals("图片上传服务暂不可用，请稍后重试", exception.getMsg());
	}

	@Test
	void untrustedStorageUrlIsRejected() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any()))
			.thenReturn("/upms/file/local/other-tenant/9f0f.png");

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadImage(TENANT, file("a.png", "image/png", png())));

		assertTrue(exception.getMsg().contains("租户隔离"));
	}

	@Test
	void fileNamePathAndLengthAreSanitized() {
		when(remoteStorageService.uploadPublicFile(any(), any(), any(), anyLong(), any())).thenReturn(STORAGE_URL);

		MessageUploadVO result = service.uploadImage(TENANT,
				file("../evil/订单截图.png", "image/png", png()));

		assertEquals("订单截图.png", result.getName());
		ArgumentCaptor<String> fileName = ArgumentCaptor.forClass(String.class);
		verify(remoteStorageService).uploadPublicFile(any(), fileName.capture(), eq("image/png"), anyLong(),
				eq("service-chat"));
		assertEquals("订单截图.png", fileName.getValue());
		verify(remoteStorageService, never()).uploadPublicFile(any(), eq("../evil/订单截图.png"), any(), anyLong(),
				any());
	}

	private MultipartFile file(String name, String contentType, byte[] content) {
		return new MockMultipartFile("file", name, contentType, content);
	}

	private byte[] png() {
		return new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02 };
	}

	private byte[] jpeg() {
		return new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46 };
	}

	private byte[] webp() {
		byte[] content = new byte[16];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 4);
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);
		return content;
	}

}
